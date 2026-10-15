# Phục hồi database HRM từ một file do backup.ps1 (hoặc backup.sh) tạo ra. Dữ liệu hiện có bị thay
# toàn bộ bằng dữ liệu trong file. Dừng API trước khi chạy.
# Cách dùng (tại thư mục gốc repo, container database đang chạy):
#   .\scripts\restore.ps1 backups\hrm-20261015-090000.sql
param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$Database = "hrm",
    [string]$User = "hrm-user"
)
$ErrorActionPreference = "Stop"
# Thông báo tiếng Việt hiển thị đúng trong console.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$File = (Resolve-Path $File).Path
Set-Location (Split-Path -Parent $PSScriptRoot)

$remote = "/tmp/hrm-restore.sql"
docker compose cp $File "database:$remote"
if ($LASTEXITCODE -ne 0) { throw "Không copy được file vào container" }
# Một transaction: lỗi ở bất kỳ câu nào thì database giữ nguyên như trước khi restore.
docker compose exec -T database psql -U $User -d $Database -v ON_ERROR_STOP=1 --single-transaction -q -o /dev/null -f $remote
$status = $LASTEXITCODE
docker compose exec -T database rm -f $remote | Out-Null
if ($status -ne 0) { throw "Restore thất bại, database không thay đổi" }

Write-Host "Đã phục hồi database '$Database' từ $File. Khởi động lại API."
