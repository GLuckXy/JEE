#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$UseExistingBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$jar = Join-Path $projectRoot 'backend/target/mianxu-product.jar'
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
    Write-Output "Starting demo on http://127.0.0.1:$Port/"
    & $java -jar $jar '--spring.profiles.active=demo' "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
