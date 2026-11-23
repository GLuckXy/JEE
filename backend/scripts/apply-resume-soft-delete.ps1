#Requires -Version 7.0
[CmdletBinding()]
param()
. "$PSScriptRoot/mysql-common.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
$sql = Get-Content -LiteralPath (Join-Path $projectRoot 'db/mysql/003_resume_soft_delete.sql') -Raw
# This helper is intentionally limited to the reviewed migration.
$expected = @(
    'ALTER TABLE resume ADD COLUMN deleted_at TIMESTAMP(6) NULL;'
    'CREATE INDEX idx_resume_user_active ON resume(user_id,deleted_at,created_at,id);'
) -join "`n"
$statements = ($sql -split '\r?\n' | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('--') }) -join "`n"
if ($statements -cne $expected) { throw 'Migration differs from the reviewed statements; review before execution.' }

Add-Type -AssemblyName System.Windows.Forms
$dialog = [Windows.Forms.Form]::new()
$dialog.Text = 'MySQL root - resume soft delete migration'
$dialog.Width = 440
$dialog.Height = 180
$dialog.StartPosition = 'CenterScreen'
$dialog.TopMost = $true
$label = [Windows.Forms.Label]::new()
$label.Text = 'Enter local MySQL root password to apply migration 003:'
$label.SetBounds(20, 15, 390, 25)
$inputBox = [Windows.Forms.TextBox]::new()
$inputBox.UseSystemPasswordChar = $true
$inputBox.SetBounds(20, 45, 380, 25)
$ok = [Windows.Forms.Button]::new()
$ok.Text = 'Apply'
$ok.SetBounds(290, 85, 110, 28)
$ok.DialogResult = [Windows.Forms.DialogResult]::OK
$dialog.Controls.AddRange(@($label, $inputBox, $ok))
$dialog.AcceptButton = $ok
try {
    if ($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { throw 'Cancelled; database unchanged.' }
    $password = $inputBox.Text
    $inputBox.Clear()
    $state = Invoke-ProjectMySql root $password @"
SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ai_interview' AND TABLE_NAME='resume' AND COLUMN_NAME='deleted_at';
SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ai_interview' AND TABLE_NAME='resume' AND INDEX_NAME='idx_resume_user_active';
"@ 'ai_interview'
    if (($state -split '\s+' | Where-Object { $_ -ne '0' }).Count -gt 0) {
        throw 'Column or index already exists. No changes applied; inspect schema before retrying.'
    }
    $before = Invoke-ProjectMySql root $password 'SELECT COUNT(*) FROM resume;' 'ai_interview'
    [void](Invoke-ProjectMySql root $password $sql 'ai_interview')
    $column = Invoke-ProjectMySql root $password "SELECT COLUMN_TYPE, IS_NULLABLE, IFNULL(COLUMN_DEFAULT,'NULL') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ai_interview' AND TABLE_NAME='resume' AND COLUMN_NAME='deleted_at';" 'ai_interview'
    $index = Invoke-ProjectMySql root $password "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ai_interview' AND TABLE_NAME='resume' AND INDEX_NAME='idx_resume_user_active';" 'ai_interview'
    if ($column -ne "timestamp(6)`tYES`tNULL" -or $index -ne 'user_id,deleted_at,created_at,id') {
        throw 'Post-migration schema verification failed. Inspect current schema; do not blindly rerun.'
    }
    $after = Invoke-ProjectMySql root $password 'SELECT COUNT(*) FROM resume;' 'ai_interview'
    Write-Output 'Migration 003 applied and verified.'
    Write-Output "deleted_at: $column"
    Write-Output "idx_resume_user_active: $index"
    Write-Output "resume rows before=$before after=$after (concurrent application writes may affect counts)."
} finally {
    $password = $null
    $dialog.Dispose()
}
