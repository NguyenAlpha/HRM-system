param(
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$PayrollUsername = 'payroll01'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    docker compose cp api/scripts/seed_demo_payroll_history.sql database:/tmp/seed_demo_payroll_history.sql
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy historical payroll seed into PostgreSQL' }
    docker compose exec -T database psql -v ON_ERROR_STOP=1 -U hrm-user -d hrm -f /tmp/seed_demo_payroll_history.sql
    if ($LASTEXITCODE -ne 0) { throw 'Historical payroll SQL seed failed' }
} finally {
    Pop-Location
}

$password = $env:DEMO_PAYROLL_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    $securePassword = Read-Host "Password for $PayrollUsername" -AsSecureString
    $password = [System.Net.NetworkCredential]::new('', $securePassword).Password
}

$loginBody = @{ usernameOrEmail = $PayrollUsername; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/api/auth/login" `
    -ContentType 'application/json' -Body $loginBody
$password = $null
$loginBody = $null
if (-not $login.success -or -not $login.data.accessToken) {
    throw 'Payroll account login failed'
}

$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$listed = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/api/payroll/periods?size=100" -Headers $headers
foreach ($month in @(7, 8, 9)) {
    $matches = @($listed.data.content | Where-Object { $_.year -eq 2026 -and $_.month -eq $month })
    if ($matches.Count -ne 1) { throw "Expected one payroll period for 2026-$month" }
    $period = $matches[0]

    if ($period.status -eq 'DRAFT') {
        $periodId = [long]$period.id
        $periodStart = [datetime]::new(2026, $month, 1).ToString('yyyy-MM-dd')
        $periodEnd = [datetime]::new(2026, $month, [datetime]::DaysInMonth(2026, $month)).ToString('yyyy-MM-dd')
        Push-Location $root
        try {
            $existingPayslips = docker compose exec -T database psql -U hrm-user -d hrm -At -c `
                "SELECT count(*) FROM payslips WHERE payroll_period_id = $periodId;"
            if ($LASTEXITCODE -ne 0 -or [int]$existingPayslips -ne 0) {
                throw "2026-$month already contains payslips; refusing to recalculate"
            }
            $otherEmployees = docker compose exec -T database psql -U hrm-user -d hrm -At -c `
                "SELECT count(*) FROM employees WHERE employee_code NOT LIKE 'DEMO-%' AND deleted_at IS NULL AND hire_date <= DATE '$periodEnd' AND (termination_date IS NULL OR termination_date >= DATE '$periodStart');"
            if ($LASTEXITCODE -ne 0 -or [int]$otherEmployees -ne 0) {
                throw "2026-$month includes employees outside the DEMO seed; refusing to calculate"
            }
        } finally {
            Pop-Location
        }
        $calculated = Invoke-RestMethod -Method Post `
            -Uri "$ApiBaseUrl/api/payroll/periods/$($period.id)/calculate" `
            -Headers $headers -ContentType 'application/json' -Body '{}'
        if (-not $calculated.success -or $calculated.data.status -ne 'CALCULATED') {
            throw "Payroll calculation failed for 2026-$month"
        }
        Write-Output "Calculated 2026-$month (period $($period.id))"
    } elseif ($period.status -in @('CALCULATED', 'APPROVED', 'PAID', 'LOCKED')) {
        Write-Output "Preserved 2026-$month ($($period.status))"
    } else {
        throw "Unexpected payroll status for 2026-$month`: $($period.status)"
    }
}

Push-Location $root
try {
    docker compose exec -T database psql -U hrm-user -d hrm -c `
        "SELECT p.year, p.month, p.status, count(s.id) AS payslips FROM payroll_periods p LEFT JOIN payslips s ON s.payroll_period_id = p.id WHERE p.year = 2026 AND p.month BETWEEN 7 AND 10 GROUP BY p.id ORDER BY p.month;"
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify historical payslips' }
} finally {
    Pop-Location
}
