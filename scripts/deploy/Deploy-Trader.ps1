# FIX-135: immutable releases; a startup timeout must never mutate a running JAR.
# Windows PowerShell 5.1. Does not repair Flyway or enable the collector consumer.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [Parameter(Mandatory=$true)][string]$ArtifactDirectory,
    [string]$DeployDirectory = 'C:\apps\crypto-ai',
    [ValidateRange(60,7200)][int]$StartupTimeoutSeconds = 1800,
    [ValidateRange(1,65535)][int]$Port = 8080
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$mutex = $null
$ownsMutex = $false
$evidence = Join-Path $ArtifactDirectory 'deployment-evidence'
$stdout = $null
$stderr = $null
$child = $null

function Get-TraderProcesses {
    # Exact jar argument, not substring matching crypto-ai-next or other Java apps.
    # Bare crypto-ai.jar supports the legacy launcher; absolute paths must be ours.
    @(Get-CimInstance Win32_Process | Where-Object {
        if ($_.Name -notmatch '^javaw?\.exe$' -or -not $_.CommandLine) { return $false }
        $match = [regex]::Match($_.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"(?<jar>[^"]+)"|(?<jar>\S+))')
        if (-not $match.Success) { return $false }
        $jar = $match.Groups['jar'].Value
        if ($jar -ieq 'crypto-ai.jar') { return $true }
        if (-not [IO.Path]::IsPathRooted($jar)) { return $false }
        $full = [IO.Path]::GetFullPath($jar)
        $root = [IO.Path]::GetFullPath($DeployDirectory).TrimEnd('\') + '\'
        return ([IO.Path]::GetFileName($full) -ieq 'crypto-ai.jar' -and
            $full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase))
    })
}

try {
    # Host-wide, cross-job exclusion in addition to Jenkins disableConcurrentBuilds.
    $mutex = New-Object System.Threading.Mutex($false, 'Global\CryptoAiTraderDeployment')
    try { $ownsMutex = $mutex.WaitOne(0) }
    catch [System.Threading.AbandonedMutexException] { $ownsMutex = $true }
    if (-not $ownsMutex) { throw 'Another Trader deployment owns the deployment mutex.' }
    if (-not [IO.Path]::IsPathRooted($JavaExe) -or -not (Test-Path -LiteralPath $JavaExe)) {
        throw 'JAVA_EXE must point to an existing absolute JDK java.exe.'
    }
    if ([IO.Path]::GetFileName($JavaExe) -ine 'java.exe' -or $JavaExe -match '(?i)javapath') {
        throw 'Use the real JDK java.exe, not javaw or an Oracle javapath launcher.'
    }
    $artifacts = @(Get-ChildItem -LiteralPath $ArtifactDirectory -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($artifacts.Count -ne 1) { throw 'Expected exactly one executable target JAR.' }
    $sourceJar = $artifacts[0].FullName
    $buildHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
    New-Item -ItemType Directory -Force -Path $DeployDirectory,$evidence | Out-Null
    $release = Join-Path $DeployDirectory ('releases\' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [guid]::NewGuid().ToString('N'))
    # No -Force: collision must fail, never overwrite any previous release.
    New-Item -ItemType Directory -Path $release | Out-Null
    $releaseJar = Join-Path $release 'crypto-ai.jar'
    Copy-Item -LiteralPath $sourceJar -Destination $releaseJar
    if ((Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash -ne $buildHash) {
        throw 'Staged JAR checksum mismatch; existing Trader has not been stopped.'
    }

    # Port alone cannot find a JVM blocked in Flyway. Stop exact Trader JVMs first.
    $existing = @(Get-TraderProcesses)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        if (@($existing | ForEach-Object { $_.ProcessId }) -notcontains $listener.OwningProcess) {
            throw "Port $Port belongs to an unrecognized process; refusing to kill it."
        }
    }
    foreach ($candidate in $existing) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($candidate.ProcessId)"
        if ($null -ne $current) {
            if ($current.CreationDate -ne $candidate.CreationDate) { throw 'Process identity changed; aborting stop.' }
            Write-Host "[FIX-135][STOP] PID=$($candidate.ProcessId)"
            Stop-Process -Id $candidate.ProcessId -Force
        }
    }
    $stopDeadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        $remaining = @(Get-TraderProcesses)
        $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
        if ($remaining.Count -eq 0 -and $listeners.Count -eq 0) { break }
        if ([DateTime]::UtcNow -ge $stopDeadline) { throw 'Trader did not fully stop; no new JVM will be launched.' }
        Start-Sleep -Seconds 1
    } while ($true)

    $stdout = Join-Path $release 'crypto-ai.log'
    $stderr = Join-Path $release 'crypto-ai-error.log'
    # Working directory preserves existing external application.yml resolution.
    # Explicit command-line flags win over accidental external LIVE settings.
    $arguments = @('-jar', ('"' + $releaseJar + '"'), "--server.port=$Port",
        '--shared-market.mode=OBSERVE', '--shared-market.activation-approved=false')
    $child = Start-Process -FilePath $JavaExe -ArgumentList $arguments -WorkingDirectory $DeployDirectory `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $record = [ordered]@{ processId=$child.Id; startTimeUtc=$child.StartTime.ToUniversalTime().ToString('o');
        java=$JavaExe; jar=$releaseJar; sha256=$buildHash; stdout=$stdout; stderr=$stderr; mode='OFF' }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $release 'process.json') -Encoding UTF8
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'process.json') -Encoding UTF8
    Write-Host "[FIX-135][START] PID=$($child.Id) JAR=$releaseJar SHA256=$buildHash"
    Write-Host "Logs: $stdout and $stderr"
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $stable = 0
    do {
        $child.Refresh()
        if ($child.HasExited) { throw "Trader exited during startup with code $($child.ExitCode)." }
        $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
        foreach ($listener in $listeners) {
            if ($listener.OwningProcess -ne $child.Id) { throw 'Another JVM owns the port; deployment is not verified.' }
        }
        $started = Select-String -LiteralPath $stdout -Pattern 'Started CryptoAiTraderApplication ' -Quiet
        if ($listeners.Count -gt 0 -and $started) { $stable++ } else { $stable = 0 }
        # Three samples five seconds apart: same launched process + startup marker.
        if ($stable -ge 3) {
            Write-Host "[FIX-135][STARTUP_VERIFIED] PID=$($child.Id); mode=OFF. This is not a business-health or LIVE acceptance test."
            break
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            throw "Startup observation timed out. PID=$($child.Id) is left untouched; inspect migrations. NO rollback or second launch."
        }
        Start-Sleep -Seconds 5
    } while ($true)
} catch {
    Write-Host "[FIX-135][DEPLOYMENT_FAILED] $($_.Exception.Message)"
    foreach ($log in @($stdout,$stderr)) {
        if ($log -and (Test-Path -LiteralPath $log)) { Get-Content -LiteralPath $log -Tail 100 }
    }
    exit 1
} finally {
    # Archive a snapshot, retaining the original open files in the release directory.
    foreach ($log in @($stdout,$stderr)) {
        if ($log -and (Test-Path -LiteralPath $log)) {
            try { Copy-Item -LiteralPath $log -Destination $evidence -Force }
            catch { Write-Warning "Could not archive log snapshot: $($_.Exception.Message)" }
        }
    }
    if ($ownsMutex) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
