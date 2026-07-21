# 紧急手动清理 Flyway 失败记录（正常情况无需使用：应用启动时会自动 repair + 幂等 migrate）。
# 用法：
#   .\scripts\repair-flyway.ps1
# 可选环境变量：DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD

param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [string]$Port = $(if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'online_safe' }),
    [string]$User = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }),
    [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '' })
)

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
    $candidates = @(
        'D:\Kf\mysql\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    )
    foreach ($path in $candidates) {
        if (Test-Path $path) {
            $mysqlPath = $path
            break
        }
    }
} else {
    $mysqlPath = $mysql.Source
}

if (-not $mysqlPath) {
    Write-Error '未找到 mysql 客户端，请把 MySQL bin 加入 PATH。'
    exit 1
}

if (-not $Password) {
    $secure = Read-Host '请输入数据库密码' -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    )
}

$sql = @'
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 删除失败记录；幂等迁移脚本允许下次启动重跑
DELETE FROM flyway_schema_history WHERE success = 0;

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
'@

Write-Host "正在修复数据库 $Database @ ${HostName}:$Port ..."
& $mysqlPath --host=$HostName --port=$Port --user=$User --password=$Password --database=$Database --execute=$sql
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Flyway 修复失败。'
    exit $LASTEXITCODE
}
Write-Host '修复完成。请重新启动后端：.\mvnw.cmd spring-boot:run'
