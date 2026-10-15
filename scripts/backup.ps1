# Sao lưu database HRM ra một file SQL (cấu trúc + dữ liệu), dùng lại được cho restore.ps1.
# Cách dùng (tại thư mục gốc repo, container database đang chạy):
#   .\scripts\backup.ps1                  -> backups\hrm-yyyyMMdd-HHmmss.sql
#   .\scripts\backup.ps1 -Output hrm.sql  -> hrm.sql
param(
    [string]$Output = "",
    [string]$Database = "hrm",
    [string]$User = "hrm-user"
)
$ErrorActionPreference = "Stop"
# Thông báo tiếng Việt hiển thị đúng trong console.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $Output) {
    New-Item -ItemType Directory -Force -Path "backups" | Out-Null
    $Output = "backups\hrm-$(Get-Date -Format 'yyyyMMdd-HHmmss').sql"
}

# Ghi file trong container rồi copy ra, để PowerShell không đổi mã hóa của dump.
$remote = "/tmp/hrm-backup.sql"
docker compose exec -T database pg_dump -U $User -d $Database --clean --if-exists --no-owner --no-privileges -f $remote
if ($LASTEXITCODE -ne 0) { throw "pg_dump thất bại" }
docker compose cp "database:$remote" $Output
if ($LASTEXITCODE -ne 0) { throw "Không copy được file sao lưu ra ngoài" }
docker compose exec -T database rm -f $remote | Out-Null

Write-Host "Đã sao lưu database '$Database' vào $Output"
