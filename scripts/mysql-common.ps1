$ErrorActionPreference = 'Stop'

function Invoke-ProjectMySql {
    param([string]$UserName, [string]$Password, [string]$Sql, [string]$Database)
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = (Get-Command mysql -ErrorAction Stop).Source
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
    foreach ($argument in @('--no-defaults', '--host=127.0.0.1', '--port=3306', "--user=$UserName", '--connect-timeout=10', '--default-character-set=utf8mb4', '--batch', '--skip-column-names')) {
        $info.ArgumentList.Add($argument)
    }
    if ($Database) { $info.ArgumentList.Add("--database=$Database") }
    # The password is passed only to the child process, never as a command argument.
    $info.Environment['MYSQL_PWD'] = $Password
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        $process.WaitForExit()
        $result = $output.GetAwaiter().GetResult()
        $errorText = $errors.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            # SQL errors can echo statements containing generated credentials.
            $code = [regex]::Match($errorText, 'ERROR\s+\d+').Value
            throw "MySQL failed ($code). SQL and credentials are withheld. Inspect database state before retrying."
        }
        return $result.Trim()
    } finally { $process.Dispose() }
}

function New-ProjectSecret {
    return [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
}

function Get-ProjectPlainText([Security.SecureString]$Secret) {
    return [System.Net.NetworkCredential]::new('', $Secret).Password
}
