#Requires -Version 7.0
[CmdletBinding()]
param([switch]$PasswordDialog)
. "$PSScriptRoot/mysql-common.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
$credentialPath = Join-Path $projectRoot 'config/local/application.yml'
$legacyCredentialPath = Join-Path (Split-Path $projectRoot -Parent) 'var/local-database.clixml'
if ((Test-Path -LiteralPath $credentialPath) -or (Test-Path -LiteralPath $legacyCredentialPath)) {
    throw 'Local credentials already exist. Configure the existing database using config/local/application.yml.'
}

if ($PasswordDialog) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = [Windows.Forms.Form]::new()
    $dialog.Text = 'AI Interview - MySQL root password'
    $dialog.Width = 440
    $dialog.Height = 180
    $dialog.StartPosition = 'CenterScreen'
    $dialog.TopMost = $true
    $label = [Windows.Forms.Label]::new()
    $label.Text = 'Enter the local MySQL root password:'
    $label.SetBounds(20, 15, 390, 25)
    $inputBox = [Windows.Forms.TextBox]::new()
    $inputBox.UseSystemPasswordChar = $true
    $inputBox.SetBounds(20, 45, 380, 25)
    $ok = [Windows.Forms.Button]::new()
    $ok.Text = 'Configure'
    $ok.SetBounds(290, 85, 110, 28)
    $ok.DialogResult = [Windows.Forms.DialogResult]::OK
    $dialog.Controls.AddRange(@($label, $inputBox, $ok))
    $dialog.AcceptButton = $ok
    try {
        if ($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { throw 'Password input cancelled; database unchanged.' }
        $rootPassword = ConvertTo-SecureString $inputBox.Text -AsPlainText -Force
        $inputBox.Clear()
    } finally { $dialog.Dispose() }
} else {
    $rootPassword = Read-Host 'Local MySQL root password' -AsSecureString
}

try {
    $rootPlain = Get-ProjectPlainText $rootPassword
    $version = Invoke-ProjectMySql root $rootPlain 'SELECT VERSION();'
    if ($version -notmatch '^(8\.|9\.)' -or [version]($version.Split('-')[0]) -lt [version]'8.0.16') {
        throw "MySQL 8.0.16+ required; detected $version."
    }
    $existing = Invoke-ProjectMySql root $rootPlain "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='ai_interview'; SELECT COUNT(*) FROM mysql.user WHERE User='ai_interview';"
    if (($existing -split '\s+' | Where-Object { $_ -ne '0' }).Count -gt 0) {
        throw 'Database or application account already exists. Nothing changed; review existing objects before proceeding.'
    }
    $appPassword = New-ProjectSecret
    $jwtSecret = New-ProjectSecret
    # Save the generated credentials before applying DDL so a partial setup is recoverable.
    [void][IO.Directory]::CreateDirectory((Split-Path $credentialPath -Parent))
    $config = @"
spring:
  datasource:
    url: 'jdbc:mysql://127.0.0.1:3306/ai_interview?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    username: 'ai_interview'
    password: '$appPassword'
app:
  jwt-secret: '$jwtSecret'
"@
    $stream = [IO.File]::Open($credentialPath, 'CreateNew', 'Write', 'None')
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($config)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
        $config = $null
        if ($bytes) { [Array]::Clear($bytes, 0, $bytes.Length) }
    }
    [void](Invoke-ProjectMySql root $rootPlain @"
CREATE DATABASE ai_interview CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'ai_interview'@'localhost' IDENTIFIED BY '$appPassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON ai_interview.* TO 'ai_interview'@'localhost';
"@)
    foreach ($migration in @('001_schema.sql', '002_initial_data.sql', '003_resume_soft_delete.sql',
            '004_content.sql', '005_training_voice.sql', '006_formal.sql', '007_report.sql')) {
        $sql = Get-Content -LiteralPath (Join-Path $projectRoot "db/mysql/$migration") -Raw -Encoding UTF8
        [void](Invoke-ProjectMySql root $rootPlain $sql 'ai_interview')
    }
    $tables = Invoke-ProjectMySql ai_interview $appPassword 'SHOW TABLES;' 'ai_interview'
    if (($tables -split '\r?\n').Count -ne 21) { throw 'Expected 21 application tables.' }
    $checks = Invoke-ProjectMySql ai_interview $appPassword "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='ai_interview' AND CONSTRAINT_TYPE='CHECK';" 'ai_interview'
    if ([int]$checks -lt 9) { throw "Expected at least 9 CHECK constraints; found $checks."
    }
    Write-Output "MySQL $version configured; application login verified; 21 tables and $checks CHECK constraints verified."
    Write-Output $tables
    Write-Output (Invoke-ProjectMySql ai_interview $appPassword 'SHOW GRANTS FOR CURRENT_USER();' 'ai_interview')
} finally {
    $rootPlain = $null
    $appPassword = $null
    $jwtSecret = $null
    if ($rootPassword) { $rootPassword.Dispose() }
}
