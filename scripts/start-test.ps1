#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Demo', 'TeamTest')][string]$Mode = 'Demo',
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$UseExistingBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$jar = Join-Path $projectRoot 'backend/target/mianxu-product.jar'
$profile = if ($Mode -eq 'Demo') { 'demo' } else { 'team-test' }
$teamConfig = 'backend/config/team-test/application.yml'
if ($Mode -eq 'TeamTest' -and -not (Test-Path -LiteralPath (Join-Path $projectRoot $teamConfig) -PathType Leaf)) {
    throw "Missing $teamConfig. Copy application.example.yml in the same directory and fill in the team credentials."
}

Push-Location -LiteralPath $projectRoot
try {
    if (-not $UseExistingBuild) {
        if (-not (Test-Path -LiteralPath 'frontend/node_modules/vite/bin/vite.js')) {
            & npm.cmd --prefix frontend ci
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        }
        & "$PSScriptRoot/build-product.ps1" -SkipTests
    }
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Build the application before using -UseExistingBuild.' }
    Write-Output "Starting $profile on http://127.0.0.1:$Port/"
    & $java -jar $jar "--spring.profiles.active=$profile" "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
