#Requires -Version 7.0
[CmdletBinding()]
param([int]$Port = 8080, [ValidatePattern('^[A-Za-z0-9._-]+\.jar$')][string]$JarName, [switch]$Product)
. "$PSScriptRoot/mysql-common.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $JarName) { $JarName = if ($Product) { 'mianxu-product.jar' } else { 'ai-inter-backend-0.1.0-SNAPSHOT.jar' } }
$jar = Join-Path $projectRoot "target/$JarName"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the application before starting it.' }
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = (Get-Command java -ErrorAction Stop).Source
$info.UseShellExecute = $false
$info.WorkingDirectory = $projectRoot
$info.ArgumentList.Add('-jar')
$info.ArgumentList.Add($jar)
if (-not ($env:DB_USERNAME -and $env:DB_PASSWORD -and $env:APP_JWT_SECRET)) {
    $credentialPath = Join-Path $projectRoot 'var/local-database.clixml'
    if (-not (Test-Path -LiteralPath $credentialPath)) {
        throw 'No local credentials. Supply DB_USERNAME, DB_PASSWORD and APP_JWT_SECRET through the current process environment.'
    }
    $config = Import-Clixml -LiteralPath $credentialPath
    $info.Environment['DB_URL'] = $config.Url
    $info.Environment['DB_USERNAME'] = $config.UserName
    $info.Environment['DB_PASSWORD'] = Get-ProjectPlainText $config.Password
    $info.Environment['APP_JWT_SECRET'] = Get-ProjectPlainText $config.JwtSecret
}
$info.Environment['SERVER_PORT'] = [string]$Port
$info.Environment['SERVER_ADDRESS'] = '127.0.0.1'
if (-not $env:APP_STORAGE_ROOT) { $info.Environment['APP_STORAGE_ROOT'] = Join-Path $projectRoot 'var/private-files' }
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
