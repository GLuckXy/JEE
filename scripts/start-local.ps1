#Requires -Version 7.0
[CmdletBinding()]
param([int]$Port = 8080, [ValidatePattern('^[A-Za-z0-9._-]+\.jar$')][string]$JarName, [switch]$Product)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $JarName) { $JarName = if ($Product) { 'mianxu-product.jar' } else { 'ai-inter-backend-0.1.0-SNAPSHOT.jar' } }
$jar = Join-Path $projectRoot "backend/target/$JarName"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the application before starting it.' }
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$info.UseShellExecute = $false
$info.WorkingDirectory = $projectRoot
$info.ArgumentList.Add('-jar')
$info.ArgumentList.Add($jar)
if (-not ($env:DB_USERNAME -and $env:DB_PASSWORD -and $env:APP_JWT_SECRET) -and
        -not (Test-Path -LiteralPath (Join-Path $projectRoot 'backend/config/local/application.yml') -PathType Leaf)) {
    throw 'Supply backend/config/local/application.yml or set DB_USERNAME, DB_PASSWORD and APP_JWT_SECRET in the process environment.'
}
$info.ArgumentList.Add("--server.port=$Port")
$info.ArgumentList.Add('--server.address=127.0.0.1')
# Never inherit bootstrap credentials for routine local startup.
[void]$info.Environment.Remove('BOOTSTRAP_ADMIN_ACCOUNT')
[void]$info.Environment.Remove('BOOTSTRAP_ADMIN_PASSWORD')
Write-Output "Starting $JarName on http://127.0.0.1:$Port/"
$process = [Diagnostics.Process]::Start($info)
$info.Environment.Clear()
try {
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Backend exited with code $($process.ExitCode)." }
} finally { $process.Dispose() }
