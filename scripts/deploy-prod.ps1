# 本机构建产物 → 上传到生产 → 服务器只打运行镜像（跳过 Maven/npm）
# 用法（在仓库根目录）:
#   powershell -File scripts/deploy-prod.ps1
# 可选:
#   -SkipBuild   跳过本机构建（已有 jar/dist 时）
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
  throw '未找到 PuTTY（plink/pscp），请先安装。'
}

if (-not $Password) {
  $secure = Read-Host -AsSecureString '生产服务器 root 密码'
  $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  )
}

function Invoke-Remote([string]$Command) {
  & $Plink -ssh -batch -hostkey $HostKey "$UserName@$HostName" -pw $Password $Command
  if ($LASTEXITCODE -ne 0) { throw "远程命令失败: $Command" }
}

function Copy-ToRemote([string]$Local, [string]$Remote) {
  & $Pscp -batch -hostkey $HostKey -pw $Password $Local "$UserName@$HostName`:$Remote"
  if ($LASTEXITCODE -ne 0) { throw "上传失败: $Local -> $Remote" }
}

$doBackend = -not $FrontendOnly
$doFrontend = -not $BackendOnly

Push-Location $Root
try {
  if (-not $SkipBuild) {
    if ($doBackend) {
      Write-Host '==> 本机构建后端 JAR' -ForegroundColor Cyan
      Push-Location "$Root\backend"
      try {
        if (Test-Path '.\mvnw.cmd') {
          & .\mvnw.cmd -B -ntp -DskipTests package
        } else {
          & mvn -B -ntp -DskipTests package
        }
        if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
      } finally { Pop-Location }
    }

    if ($doFrontend) {
      Write-Host '==> 本机构建前端 dist' -ForegroundColor Cyan
      Push-Location "$Root\frontend"
      try {
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci 失败' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
      } finally { Pop-Location }
    }
  }

  $jar = Get-ChildItem "$Root\backend\target\*.jar" |
    Where-Object { $_.Name -notlike '*.original' } |
    Select-Object -First 1
  if ($doBackend -and -not $jar) { throw '未找到 backend/target/*.jar' }
  if ($doFrontend -and -not (Test-Path "$Root\frontend\dist\index.html")) {
    throw '未找到 frontend/dist'
  }

  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  $localStage = Join-Path $Root ".tmp-deploy\stage-$stamp"
  New-Item -ItemType Directory -Force -Path $localStage | Out-Null

  if ($doBackend) {
    Copy-Item $jar.FullName (Join-Path $localStage 'app.jar')
    Copy-Item $jar.FullName (Join-Path $Root 'backend\app.jar') -Force
  }
  if ($doFrontend) {
    Compress-Archive -Path "$Root\frontend\dist\*" -DestinationPath (Join-Path $localStage 'frontend-dist.zip') -Force
  }

  Write-Host '==> 上传产物到服务器' -ForegroundColor Cyan
  Invoke-Remote "mkdir -p $RemoteRepo/backend/target $RemoteRepo/frontend/dist /tmp/online-safe-stage"
  if ($doBackend) {
    Copy-ToRemote (Join-Path $localStage 'app.jar') '/tmp/online-safe-stage/app.jar'
    Invoke-Remote "rm -f $RemoteRepo/backend/target/*.jar $RemoteRepo/backend/app.jar && cp /tmp/online-safe-stage/app.jar $RemoteRepo/backend/app.jar && mkdir -p $RemoteRepo/backend/target && cp /tmp/online-safe-stage/app.jar $RemoteRepo/backend/target/app.jar"
  }
  if ($doFrontend) {
    Copy-ToRemote (Join-Path $localStage 'frontend-dist.zip') '/tmp/online-safe-stage/frontend-dist.zip'
    Invoke-Remote "rm -rf $RemoteRepo/frontend/dist/* && unzip -qo /tmp/online-safe-stage/frontend-dist.zip -d $RemoteRepo/frontend/dist"
  }

  # 确保 runtime Dockerfile 与 compose 覆盖文件在服务器上
  Copy-ToRemote "$Root\backend\Dockerfile.runtime" "$RemoteRepo/backend/Dockerfile.runtime"
  Copy-ToRemote "$Root\frontend\Dockerfile.runtime" "$RemoteRepo/frontend/Dockerfile.runtime"
  Copy-ToRemote "$Root\deploy\compose.prebuilt.yml" "$RemoteRepo/deploy/compose.prebuilt.yml"

  Write-Host '==> 服务器仅打包运行镜像并重启' -ForegroundColor Cyan
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

  Write-Host '==> 部署完成（产物构建模式）' -ForegroundColor Green
} finally {
  Pop-Location
}
