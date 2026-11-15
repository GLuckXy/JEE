#Requires -Version 7.0
[CmdletBinding()]
param([ValidateRange(1, 65535)][int]$Port = 8080)
. "$PSScriptRoot/mysql-common.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
$config = Import-Clixml -LiteralPath (Join-Path $projectRoot 'var/local-database.clixml')
try {
    $variables = [ordered]@{
        DB_URL = $config.Url
        DB_USERNAME = $config.UserName
        DB_PASSWORD = Get-ProjectPlainText $config.Password
        APP_JWT_SECRET = Get-ProjectPlainText $config.JwtSecret
        SERVER_PORT = [string]$Port
        APP_STORAGE_ROOT = Join-Path $projectRoot 'var/private-files'
    }
    if ([Text.Encoding]::UTF8.GetByteCount($variables.APP_JWT_SECRET) -lt 32) {
        throw 'Saved JWT secret is invalid. Existing credentials were not changed.'
    }
    $entries = foreach ($entry in $variables.GetEnumerator()) {
        if ([string]::IsNullOrWhiteSpace($entry.Value) -or $entry.Value -match '[;\r\n]') {
            throw "Cannot export $($entry.Key) as an IDEA environment entry."
        }
        "$($entry.Key)=$($entry.Value)"
    }
    Set-Clipboard -Value ($entries -join ';')
    Write-Output 'Copied local environment variables. Paste into IDEA Run > Edit Configurations > Environment variables.'
    Write-Output 'The clipboard contains credentials. Keep the run configuration local; copy ordinary text after pasting.'
} finally {
    $variables = $null
    $entries = $null
    if ($config.Password) { $config.Password.Dispose() }
    if ($config.JwtSecret) { $config.JwtSecret.Dispose() }
}
