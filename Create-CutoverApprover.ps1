param(
    [string]$JarPath = 'C:\apps\crypto-ai\releases\20260928T085652049Z-191a0ae9425b4e1393ecd3f85d21baab\crypto-ai.jar',
    [string]$JavaExe = 'C:\Program Files\Java\jdk-21.0.12\bin\java.exe'
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $JarPath)) { throw "Trader JAR not found: $JarPath" }
if (!(Test-Path -LiteralPath $JavaExe)) { throw "Java not found: $JavaExe" }
$work = Join-Path ([IO.Path]::GetTempPath()) ('cutover-password-' + [Guid]::NewGuid().ToString('N'))
$archive = $null
$process = $null
$secret = $null
$pointer = [IntPtr]::Zero
try {
    $lib = Join-Path $work 'lib'
    New-Item -ItemType Directory -Path $lib -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $lib $entry.Name), $false)
        }
    }
    $archive.Dispose()
    $archive = $null
    $source = Join-Path $work 'CutoverPasswordHash.java'
    @'
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class CutoverPasswordHash {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes.");
        System.out.print("{bcrypt}" + new BCryptPasswordEncoder(12).encode(password));
    }
}
'@ | Set-Content -LiteralPath $source -Encoding UTF8
    # Java source launch reads UTF-8; remove the PowerShell 5.1 BOM.
    $sourceText = [IO.File]::ReadAllText($source)
    [IO.File]::WriteAllText($source, $sourceText, (New-Object System.Text.UTF8Encoding($false)))
    $secret = Read-Host 'Choose a NEW password for cutover_approver (12+ characters; remember it; do not send it in chat)' -AsSecureString
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $JavaExe
    $info.Arguments = '--class-path "' + $lib + '\*" "' + $source + '"'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    [void]$process.Start()
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $passwordBytes = $null
    try {
        $passwordBytes = [Text.Encoding]::UTF8.GetBytes([Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) + "`n")
        $process.StandardInput.BaseStream.Write($passwordBytes, 0, $passwordBytes.Length)
        $process.StandardInput.BaseStream.Flush()
    }
    finally {
        if ($null -ne $passwordBytes) { [Array]::Clear($passwordBytes, 0, $passwordBytes.Length) }
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $pointer = [IntPtr]::Zero
        $process.StandardInput.Close()
    }
    $output = $process.StandardOutput.ReadToEnd()
    $errors = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Password hashing failed: $errors" }
    $hash = $output.Trim()
    if ($hash -notmatch '^\{bcrypt\}\$2[aby]\$12\$[./A-Za-z0-9]{53}$') { throw 'Unexpected hash output; no SQL generated.' }
    Write-Host ''
    Write-Host 'Copy the SQL below into MySQL Workbench and execute it. This script has NOT connected to the database.'
    Write-Output @"
INSERT INTO crypto_ai.app_user (username, password, role_name, enabled)
VALUES ('cutover_approver', '$hash', 'CUTOVER_APPROVER', 1);

SELECT username, enabled, role_name
FROM crypto_ai.app_user
WHERE username = 'cutover_approver';
"@
    Write-Host 'If INSERT reports a duplicate username, stop and report that error; do not overwrite an existing account.'
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($null -ne $archive) { $archive.Dispose() }
    if ($null -ne $process) { $process.Dispose() }
    if ($null -ne $secret) { $secret.Dispose() }
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}
