#Requires -Version 7.0
[CmdletBinding()]
param([switch]$Apply, [switch]$PasswordDialog)

. "$PSScriptRoot/mysql-common.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
$database = 'ai_interview'
$baseTables = @('sys_user', 'auth_session', 'file_object', 'resume', 'resume_version', 'resume_project', 'user_audit_log')
$migrations = [ordered]@{
    '004_content.sql' = @('content_job', 'content_category', 'content_question', 'content_demo', 'content_demo_stage')
    '005_training_voice.sql' = @('training_session', 'training_answer', 'voice_asset')
    '006_formal.sql' = @('fi_session', 'fi_audio')
    '007_report.sql' = @('rp_evaluation_rule', 'rp_report', 'rp_answer_evaluation', 'rp_retrain_link')
    '008_operation_log.sql' = @('operation_log')
}
$moduleTables = @($migrations.Values | ForEach-Object { $_ })
$statements = foreach ($migration in $migrations.GetEnumerator()) {
    $lines = Get-Content -LiteralPath (Join-Path $projectRoot "db/mysql/$($migration.Key)") -Encoding UTF8
    $sql = ($lines | Where-Object { -not $_.TrimStart().StartsWith('--') }) -join "`n"
    $definitions = @($sql.Split(';') | ForEach-Object Trim | Where-Object { $_ })
    $names = foreach ($definition in $definitions) {
        if ($definition -cnotmatch '^CREATE TABLE IF NOT EXISTS ([a-z_]+)\s*\(') {
            throw "Unexpected SQL in $($migration.Key); no changes applied."
        }
        $Matches[1]
    }
    if (($names -join ',') -cne ($migration.Value -join ',')) {
        throw "Unexpected table definitions in $($migration.Key); no changes applied."
    }
    $sql -replace 'CREATE TABLE IF NOT EXISTS ', 'CREATE TABLE '
}

if ($PasswordDialog) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = [Windows.Forms.Form]::new()
    $dialog.Text = 'AI Interview - MySQL migration 004-008'
    $dialog.ClientSize = [Drawing.Size]::new(425, 124)
    $dialog.StartPosition = 'CenterScreen'
    $dialog.TopMost = $true
    $label = [Windows.Forms.Label]::new()
    $label.Text = 'Enter the local MySQL root password for ai_interview:'
    $label.SetBounds(16, 15, 380, 22)
    $inputBox = [Windows.Forms.TextBox]::new()
    $inputBox.UseSystemPasswordChar = $true
    $inputBox.SetBounds(16, 43, 392, 25)
    $button = [Windows.Forms.Button]::new()
    $button.Text = if ($Apply) { 'Check and apply' } else { 'Check only' }
    $button.SetBounds(278, 78, 130, 30)
    $button.DialogResult = [Windows.Forms.DialogResult]::OK
    $dialog.Controls.AddRange(@($label, $inputBox, $button))
    $dialog.AcceptButton = $button
    try {
        if ($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) {
            Write-Output 'Cancelled; no changes applied.'
            return
        }
        $secret = ConvertTo-SecureString $inputBox.Text -AsPlainText -Force
        $inputBox.Clear()
    } finally { $dialog.Dispose() }
} else {
    $secret = Read-Host 'Local MySQL root password (ai_interview)' -AsSecureString
}

try {
    $password = Get-ProjectPlainText $secret
    $version = Invoke-ProjectMySql root $password 'SELECT VERSION();' $database
    if ($version -notmatch '^(8|9)\.' -or [version]($version.Split('-')[0]) -lt [version]'8.0.16') {
        throw "Expected MySQL 8.0.16+; found $version."
    }
    $collations = Invoke-ProjectMySql root $password @'
SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE();
SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'id';
'@ $database
    if (($collations -split '\r?\n' -join ',') -ne 'utf8mb4_0900_ai_ci,utf8mb4_0900_ai_ci') {
        throw 'Schema or sys_user.id collation differs from the integration schema. No changes applied.'
    }
    $objectsSql = 'SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();'
    $objects = (Invoke-ProjectMySql root $password $objectsSql $database) -split '\r?\n'
    $missingBase = @($baseTables | Where-Object { $_ -cnotin $objects })
    if ($missingBase.Count) { throw "Missing baseline tables: $($missingBase -join ', '). No changes applied." }
    $resume = Invoke-ProjectMySql root $password @'
SELECT CONCAT(COLUMN_TYPE, ':', IS_NULLABLE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'deleted_at';
SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND INDEX_NAME = 'idx_resume_user_active';
'@ $database
    if (($resume -split '\r?\n' -join ';') -ne 'timestamp(6):YES;user_id,deleted_at,created_at,id') {
        throw 'Migration 003 is missing or inconsistent. Inspect and reconcile it separately; no changes applied.'
    }
    $present = @($moduleTables | Where-Object { $_ -cin $objects })
    if ($present.Count -eq $moduleTables.Count) {
        Write-Output 'All 15 integration table names already exist; no migration applied. Verify their layout before deployment.'
        return
    }
    if ($present.Count) { throw "Partial upgrade detected ($($present -join ', ')); reconcile before retrying. No changes applied." }
    Write-Output "Preflight passed: MySQL $version, 7 baseline tables and migration 003 verified."
    if (-not $Apply) { Write-Output 'Check only: migrations 004-008 are pending.'; return }

    $backup = Join-Path $projectRoot "var/migration-backups/ai_interview-before-004-$([guid]::NewGuid()).sql"
    [void][IO.Directory]::CreateDirectory((Split-Path $backup -Parent))
    $schema = foreach ($table in $baseTables) {
        $create = Invoke-ProjectMySql root $password "SHOW CREATE TABLE $table;" $database
        ($create.Split("`t", 2)[1].Replace('\n', "`n")) + ';'
    }
    [IO.File]::WriteAllLines($backup, [string[]]$schema)
    Write-Output "Baseline schema snapshot: $backup"
    try {
        [void](Invoke-ProjectMySql root $password "SET SESSION default_storage_engine = InnoDB;`n$($statements -join "`n")" $database)
        $objects = (Invoke-ProjectMySql root $password $objectsSql $database) -split '\r?\n'
        $missing = @($moduleTables | Where-Object { $_ -cnotin $objects })
        if ($missing.Count) { throw "Missing integration tables: $($missing -join ', ')" }
    } catch { throw "Upgrade may be partial because MySQL DDL commits per statement. Do not retry blindly. $($_.Exception.Message)" }
    Write-Output 'Migrations 004-008 applied. All 15 new tables verified; existing tables not written.'
} finally {
    $password = $null
    if ($secret) { $secret.Dispose() }
}
