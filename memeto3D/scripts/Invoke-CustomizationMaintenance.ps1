param([switch]$Apply)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$names=@('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','DB_PASSWORD','B2_ENDPOINT','B2_REGION','B2_BUCKET','B2_KEY_ID','B2_APPLICATION_KEY')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
    foreach($line in [IO.File]::ReadAllLines((Join-Path $projectRoot '.env'))) {
        if($line -notmatch '^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*)$'){continue}
        $name=$Matches[1];if($names -notcontains $name){continue}
        if(-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))){continue}
        $value=$Matches[2].Trim()
        if(($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))){$value=$value.Substring(1,$value.Length-2)}else{$value=($value -replace '\s+#.*$','').TrimEnd()}
        [Environment]::SetEnvironmentVariable($name,$value,'Process')
    }
    if(-not $env:DB_PORT){$env:DB_PORT='5432'}
    Push-Location -LiteralPath $projectRoot
    try {
        & .\mvnw.cmd -q '-DskipTests' compile dependency:build-classpath '-Dmdep.outputFile=target/customization-maintenance-classpath.txt'
        if($LASTEXITCODE -ne 0){throw 'Compile/classpath preparation failed'}
        $cp='target/classes;'+[IO.File]::ReadAllText((Join-Path $projectRoot 'target/customization-maintenance-classpath.txt')).Trim()
        $arguments=@('--class-path',$cp,'scripts/CustomizationMaintenance.java')
        if($Apply){$arguments+='--apply'}
        & java @arguments
        if($LASTEXITCODE -ne 0){throw 'Maintenance stopped; no automatic repair or rollback attempted'}
    } finally {Pop-Location}
} finally {foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}}
