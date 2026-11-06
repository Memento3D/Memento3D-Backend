param(
    [switch]$ConfigureCors,
    [string[]]$Origins = @('http://localhost:5173', 'http://127.0.0.1:5173')
)
$ErrorActionPreference = 'Stop'
$b2ProjectRoot = Split-Path -Parent $PSScriptRoot
$b2EnvPath = Join-Path $b2ProjectRoot '.env'
if (-not (Test-Path -LiteralPath $b2EnvPath)) { throw 'Create memeto3D/.env and add the B2 settings first.' }
# Read only B2 settings as data. Never echo or execute dotenv content.
foreach ($b2Line in [System.IO.File]::ReadAllLines($b2EnvPath)) {
    if ($b2Line -notmatch '^\s*(?:export\s+)?(B2_ENDPOINT|B2_REGION|B2_BUCKET|B2_KEY_ID|B2_APPLICATION_KEY|B2_URL_TTL_SECONDS)\s*=\s*(.*)$') { continue }
    $b2Setting = $Matches[1]
    $b2Value = $Matches[2].Trim()
    if (($b2Value.StartsWith('"') -and $b2Value.EndsWith('"')) -or
        ($b2Value.StartsWith("'") -and $b2Value.EndsWith("'"))) {
        if ($b2Value.Length -lt 2) { throw 'Invalid quoted B2 setting.' }
        $b2Value = $b2Value.Substring(1, $b2Value.Length - 2)
    } else { $b2Value = ($b2Value -replace '\s+#.*$', '').TrimEnd() }
    [Environment]::SetEnvironmentVariable($b2Setting, $b2Value, 'Process')
}
foreach ($b2Required in @('B2_ENDPOINT','B2_REGION','B2_BUCKET','B2_KEY_ID','B2_APPLICATION_KEY')) {
    $b2Configured = [Environment]::GetEnvironmentVariable($b2Required, 'Process')
    if ([string]::IsNullOrWhiteSpace($b2Configured) -or $b2Configured.StartsWith('thay_bang_')) {
        throw "Set $b2Required in memeto3D/.env first. Do not paste secrets into chat."
    }
}
$env:B2_RUN_LIVE_CHECK = 'true'
$env:B2_CONFIGURE_CORS = if ($ConfigureCors) { 'true' } else { 'false' }
$env:B2_CORS_ORIGINS = $Origins -join ','
Push-Location -LiteralPath $b2ProjectRoot
try {
    & ./mvnw.cmd '-Dtest=B2LiveStorageTest' test
    if ($LASTEXITCODE -ne 0) { throw 'B2 live check failed. Inspect the exception above; do not share credentials.' }
} finally {
    Remove-Item Env:B2_RUN_LIVE_CHECK -ErrorAction SilentlyContinue
    Remove-Item Env:B2_CONFIGURE_CORS -ErrorAction SilentlyContinue
    Remove-Item Env:B2_KEY_ID -ErrorAction SilentlyContinue
    Remove-Item Env:B2_APPLICATION_KEY -ErrorAction SilentlyContinue
    Pop-Location
}
