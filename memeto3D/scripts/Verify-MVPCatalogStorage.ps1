param()
$ErrorActionPreference = 'Stop'
$catalogProjectRoot = Split-Path -Parent $PSScriptRoot
$catalogEnvPath = Join-Path $catalogProjectRoot '.env'
$catalogNames = @('B2_ENDPOINT','B2_REGION','B2_BUCKET','B2_KEY_ID','B2_APPLICATION_KEY')
$catalogPrevious = @{}
foreach ($name in $catalogNames + @('B2_RUN_CATALOG_READ_CHECK')) { $catalogPrevious[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    if (Test-Path -LiteralPath $catalogEnvPath) {
        foreach ($line in [IO.File]::ReadAllLines($catalogEnvPath)) {
            if ($line -notmatch '^\s*(?:export\s+)?(B2_ENDPOINT|B2_REGION|B2_BUCKET|B2_KEY_ID|B2_APPLICATION_KEY)\s*=\s*(.*)$') { continue }
            $name = $Matches[1]
            if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))) { continue }
            $value = $Matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                if ($value.Length -lt 2) { throw 'Invalid quoted B2 setting' }
                $value = $value.Substring(1,$value.Length-2)
            } else { $value = ($value -replace '\s+#.*$','').TrimEnd() }
            [Environment]::SetEnvironmentVariable($name,$value,'Process')
        }
    }
    foreach ($name in $catalogNames) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))) { throw "Missing $name. Configure it locally; do not send keys in chat." }
    }
    $env:B2_RUN_CATALOG_READ_CHECK = 'true'
    Push-Location -LiteralPath $catalogProjectRoot
    try {
        & .\mvnw.cmd '-Dtest=CatalogB2LiveTest' test
        if ($LASTEXITCODE -ne 0) { throw 'Read-only catalog verification failed. No catalog was imported.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $catalogPrevious.Keys) { [Environment]::SetEnvironmentVariable($name,$catalogPrevious[$name],'Process') }
}
