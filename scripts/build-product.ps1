#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$work = Join-Path $projectRoot "target/product-build/$([guid]::NewGuid().ToString('N'))"
$assets = Join-Path $work 'ui'
$build = Join-Path $work 'maven'
$frontend = Join-Path $projectRoot 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules/vite/bin/vite.js'))) {
    throw 'Install the frontend project dependencies before building the release.'
}
Push-Location -LiteralPath $frontend
try {
    & npm.cmd run build -- --outDir $assets --emptyOutDir false
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $assets 'index.html'))) {
        throw 'Frontend release build failed.'
    }
} finally { Pop-Location }

Push-Location -LiteralPath $projectRoot
try {
    & mvn.cmd -Pproduct "-Dapp.ui.directory=$assets" "-Dapp.build.directory=$build" package
    if ($LASTEXITCODE -ne 0) { throw 'Backend release build failed.' }
    $jar = Join-Path $build 'mianxu-product.jar'
    $archive = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in @('static/index.html', 'static/favicon.svg', 'application.yml', 'teaching/content-samples.json')) {
            if (-not $archive.GetEntry("BOOT-INF/classes/$entry")) { throw "Release is missing $entry." }
        }
        if (-not ($archive.Entries.FullName -match '^BOOT-INF/classes/static/assets/[^/]+\.js$')) {
            throw 'Release is missing bundled JavaScript.'
        }
    } finally { $archive.Dispose() }
    $release = Join-Path $projectRoot 'target/mianxu-product.jar'
    Copy-Item -LiteralPath $jar -Destination $release -Force
    Write-Output "Release: $release"
} finally { Pop-Location }
