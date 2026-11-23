#Requires -Version 7.0
[CmdletBinding()]
param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $projectRoot 'backend'
$work = Join-Path $backend "target/product-build/$([guid]::NewGuid().ToString('N'))"
$assets = Join-Path $work 'ui'
$build = Join-Path $work 'maven'
$frontend = Join-Path $projectRoot 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules/vite/bin/vite.js'))) {
    throw 'Run npm.cmd --prefix frontend ci from the repository root before building the release.'
}
Push-Location -LiteralPath $frontend
try {
    & npm.cmd run build -- --outDir $assets --emptyOutDir false
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $assets 'index.html'))) {
        throw 'Frontend release build failed.'
    }
} finally { Pop-Location }

Push-Location -LiteralPath $backend
try {
    $mavenArguments = @('-Pproduct', "-Dapp.ui.directory=$assets", "-Dapp.build.directory=$build")
    if ($SkipTests) { $mavenArguments += '-DskipTests=true' }
    $mavenArguments += 'package'
    & mvn.cmd @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend release build failed.' }
    $jar = Join-Path $build 'mianxu-product.jar'
    $archive = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in @('static/index.html', 'static/favicon.svg', 'application.yml', 'teaching/content-samples.json',
                'schema-h2.sql', 'migrations/007_report.sql')) {
            if (-not $archive.GetEntry("BOOT-INF/classes/$entry")) { throw "Release is missing $entry." }
        }
        if (-not $archive.GetEntry('META-INF/spring.factories')) { throw 'Release is missing test launch registration.' }
        if (-not ($archive.Entries.FullName -match '^BOOT-INF/classes/static/assets/[^/]+\.js$')) {
            throw 'Release is missing bundled JavaScript.'
        }
    } finally { $archive.Dispose() }
    $release = Join-Path $backend 'target/mianxu-product.jar'
    Copy-Item -LiteralPath $jar -Destination $release -Force
    Write-Output "Release: $release"
} finally { Pop-Location }
