# Build artifacts locally, upload to prod, rebuild runtime images only (skip Maven/npm on server).
# Usage (repo root):
#   powershell -File scripts/deploy-prod.ps1
# Options:
#   -SkipBuild   skip local build when jar/dist already exist
#   -BackendOnly / -FrontendOnly

param(
  [switch]$SkipBuild,
  [switch]$BackendOnly,
  [switch]$FrontendOnly,
  [string]$HostName = '124.222.9.209',
  [string]$UserName = 'root',
  [string]$HostKey = 'SHA256:KE9CvZ0QiGG0H4c8556t/CdsBMDeeazZZKLkDHZo+pY',
  [string]$RemoteRepo = '/opt/online-safe/repo',
  [string]$Password
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Plink = 'C:\Program Files\PuTTY\plink.exe'
$Pscp = 'C:\Program Files\PuTTY\pscp.exe'

if (-not (Test-Path $Plink) -or -not (Test-Path $Pscp)) {
  throw 'PuTTY (plink/pscp) not found. Please install PuTTY first.'
}

if (-not $Password) {
  $secure = Read-Host -AsSecureString 'Production root password'
  $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  )
}

function Invoke-Remote([string]$Command) {
  & $Plink -ssh -batch -hostkey $HostKey "$UserName@$HostName" -pw $Password $Command
  if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Command" }
}

function Copy-ToRemote([string]$Local, [string]$Remote) {
  & $Pscp -batch -hostkey $HostKey -pw $Password $Local "$UserName@$HostName`:$Remote"
  if ($LASTEXITCODE -ne 0) { throw "Upload failed: $Local -> $Remote" }
}

$doBackend = -not $FrontendOnly
$doFrontend = -not $BackendOnly

Push-Location $Root
try {
  if (-not $SkipBuild) {
    if ($doBackend) {
      Write-Host '==> Build backend JAR' -ForegroundColor Cyan
      Push-Location "$Root\backend"
      try {
        if (Test-Path '.\mvnw.cmd') {
          & .\mvnw.cmd -B -ntp -DskipTests package
        } else {
          & mvn -B -ntp -DskipTests package
        }
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
      } finally { Pop-Location }
    }

    if ($doFrontend) {
      Write-Host '==> Build frontend dist' -ForegroundColor Cyan
      Push-Location "$Root\frontend"
      try {
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
      } finally { Pop-Location }
    }
  }

  $jar = Get-ChildItem "$Root\backend\target\*.jar" |
    Where-Object { $_.Name -notlike '*.original' } |
    Select-Object -First 1
  if ($doBackend -and -not $jar) { throw 'backend/target/*.jar not found' }
  if ($doFrontend -and -not (Test-Path "$Root\frontend\dist\index.html")) {
    throw 'frontend/dist not found'
  }

  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  $localStage = Join-Path $Root ".tmp-deploy\stage-$stamp"
  New-Item -ItemType Directory -Force -Path $localStage | Out-Null

  if ($doBackend) {
    Copy-Item $jar.FullName (Join-Path $localStage 'app.jar')
    Copy-Item $jar.FullName (Join-Path $Root 'backend\app.jar') -Force
  }
  if ($doFrontend) {
    # Prefer tar.gz: Windows Compress-Archive uses backslashes and breaks Linux unzip.
    $distTar = Join-Path $localStage 'frontend-dist.tar.gz'
    Push-Location "$Root\frontend\dist"
    try {
      & tar -czf $distTar *
      if ($LASTEXITCODE -ne 0) { throw 'Failed to pack frontend dist with tar' }
    } finally { Pop-Location }
  }

  Write-Host '==> Upload artifacts to server' -ForegroundColor Cyan
  Invoke-Remote "mkdir -p $RemoteRepo/backend/target $RemoteRepo/frontend/dist /tmp/online-safe-stage"
  if ($doBackend) {
    Copy-ToRemote (Join-Path $localStage 'app.jar') '/tmp/online-safe-stage/app.jar'
    Invoke-Remote "rm -f $RemoteRepo/backend/target/*.jar $RemoteRepo/backend/app.jar && cp /tmp/online-safe-stage/app.jar $RemoteRepo/backend/app.jar && mkdir -p $RemoteRepo/backend/target && cp /tmp/online-safe-stage/app.jar $RemoteRepo/backend/target/app.jar"
  }
  if ($doFrontend) {
    Copy-ToRemote (Join-Path $localStage 'frontend-dist.tar.gz') '/tmp/online-safe-stage/frontend-dist.tar.gz'
    Invoke-Remote "rm -rf $RemoteRepo/frontend/dist/* && tar -xzf /tmp/online-safe-stage/frontend-dist.tar.gz -C $RemoteRepo/frontend/dist"
  }

  # Ensure runtime Dockerfiles and compose overlay exist on server
  Copy-ToRemote "$Root\backend\Dockerfile.runtime" "$RemoteRepo/backend/Dockerfile.runtime"
  Copy-ToRemote "$Root\frontend\Dockerfile.runtime" "$RemoteRepo/frontend/Dockerfile.runtime"
  Copy-ToRemote "$Root\deploy\compose.prebuilt.yml" "$RemoteRepo/deploy/compose.prebuilt.yml"

  Write-Host '==> Build runtime images and restart' -ForegroundColor Cyan
  $services = @()
  if ($doBackend) { $services += 'backend' }
  if ($doFrontend) { $services += 'frontend' }
  $svc = ($services -join ' ')
  Invoke-Remote @"
set -e
cd $RemoteRepo/deploy
docker compose -f compose.yml -f compose.prebuilt.yml build $svc
docker compose -f compose.yml -f compose.prebuilt.yml up -d $svc
docker compose ps
docker exec online-safe-backend-1 curl -fsS http://127.0.0.1:8080/actuator/health
echo
"@

  Write-Host '==> Deploy finished (prebuilt artifacts mode)' -ForegroundColor Green
} finally {
  Pop-Location
}
