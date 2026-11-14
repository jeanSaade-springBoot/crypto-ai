# FIX-135: immutable releases; a startup timeout must never mutate a running JAR.
# FIX-153: Windows PowerShell 5.1. Forces local OFF mode and shared schedulers off.
# One trader per database must also be enforced across hosts/containers externally.
# Jenkins completion: redirect stdin, archive bounded log tails, and explicitly exit.
# Does not repair Flyway or automatically roll back a failed deployment.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [Parameter(Mandatory=$true)][string]$ArtifactDirectory,
    [string]$DeployDirectory = 'C:\apps\crypto-ai',
    [ValidateRange(60,7200)][int]$StartupTimeoutSeconds = 1800,
    [ValidateRange(1,65535)][int]$Port = 8080,
    # FIX-153: stable host path shared by service/IDE launches, never a release-local path.
    [string]$InstanceLockPath,
    # Must request application-level quiesce and return only after the request was accepted.
    # Required when replacing a running trader; process termination is never the drain.
    [scriptblock]$DrainCommand
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
    # Exact JAR argument; absolute paths must be inside this deployment root.
    # Bare crypto-ai.jar supports the legacy launcher.
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
    # Host-wide exclusion in addition to Jenkins disableConcurrentBuilds.
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
    # FIX-153: normalize paths before staging/stopping so every release uses one lock.
    if (-not [IO.Path]::IsPathRooted($DeployDirectory)) { throw 'DeployDirectory must be absolute.' }
    $DeployDirectory = [IO.Path]::GetFullPath($DeployDirectory)
    if ([string]::IsNullOrWhiteSpace($InstanceLockPath)) {
        $InstanceLockPath = Join-Path $DeployDirectory 'logs\crypto-ai-trader.instance.lock'
    }
    if (-not [IO.Path]::IsPathRooted($InstanceLockPath)) { throw 'InstanceLockPath must be absolute.' }
    $InstanceLockPath = [IO.Path]::GetFullPath($InstanceLockPath)
    Write-Host "[FIX-153][DEPLOYMENT_SCOPE] mode=OFF lockPath=$InstanceLockPath; host/path exclusion only. Deployment must prevent another host/container from sharing this database."
    $artifacts = @(Get-ChildItem -LiteralPath $ArtifactDirectory -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($artifacts.Count -ne 1) { throw 'Expected exactly one executable target JAR.' }
    $sourceJar = $artifacts[0].FullName
    # FIX-153: fail before stopping the existing process if an old executable was staged.
    # Presence is a packaging guard, not proof that migration or business health succeeds.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($sourceJar)
    try {
        foreach ($requiredEntry in @(
            'BOOT-INF/classes/db/migration/V106__fix_153_local_candle_evidence.sql',
            'BOOT-INF/classes/db/migration/B105__fix_153_trader_only_baseline.sql',
            'BOOT-INF/classes/com/crypto/local/LocalTraderInstance.class')) {
            if ($null -eq $archive.GetEntry($requiredEntry)) {
                throw "Artifact is missing FIX-153 resource: $requiredEntry; existing trader remains untouched."
            }
        }
    } finally { $archive.Dispose() }
    $buildHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
    New-Item -ItemType Directory -Force -Path $DeployDirectory,$evidence | Out-Null
    $release = Join-Path $DeployDirectory ('releases\' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [guid]::NewGuid().ToString('N'))
    # No -Force: never overwrite an existing release.
    New-Item -ItemType Directory -Path $release | Out-Null
    $releaseJar = Join-Path $release 'crypto-ai.jar'
    Copy-Item -LiteralPath $sourceJar -Destination $releaseJar
    if ((Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash -ne $buildHash) {
        throw 'Staged JAR checksum mismatch; existing Trader has not been stopped.'
    }

    # Stop exact Trader JVMs, including a JVM that has not opened its port yet.
    $existing = @(Get-TraderProcesses)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        if (@($existing | ForEach-Object { $_.ProcessId }) -notcontains $listener.OwningProcess) {
            throw "Port $Port belongs to an unrecognized process; refusing to kill it."
        }
    }
    if ($existing.Count -gt 0) {
        if ($null -eq $DrainCommand) { throw 'A running trader requires -DrainCommand. It must quiesce the application and request graceful shutdown; deployment will not force-kill it.' }
        Write-Host "[FIX-153][DRAIN_REQUEST] requesting application drain for $($existing.Count) trader process(es)"
        $global:LASTEXITCODE = 0
        & $DrainCommand
        if (-not $?) { throw 'Drain command reported a PowerShell failure; existing trader remains running.' }
        if ($LASTEXITCODE -ne 0) { throw "Drain command failed with exit code $LASTEXITCODE; existing trader remains running." }
    }
    # The application receives a 60s drain budget; leave an independent exit margin.
    $stopDeadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        $remaining = @(Get-TraderProcesses)
        $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
        if ($remaining.Count -eq 0 -and $listeners.Count -eq 0) { break }
        if ([DateTime]::UtcNow -ge $stopDeadline) { throw 'Trader did not drain and exit within the bound; no force-kill and no new JVM will be launched.' }
        Start-Sleep -Seconds 1
    } while ($true)

    $logDirectory = Join-Path $DeployDirectory 'logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $stdout = Join-Path $logDirectory 'crypto-ai-stdout.log'
    $stderr = Join-Path $logDirectory 'crypto-ai-stderr.log'
    # FIX-153: command-line properties override inherited YAML/environment settings.
    # Retain external application.yml resolution; force only the local runtime contract.
    # Stable lock is shared across releases and must also be used by manual/IDE launches.
    $arguments = @('-jar', ('"' + $releaseJar + '"'), "--server.port=$Port",
        '--shared-market.mode=OFF', '--shared-market.activation-approved=false',
        '--shared-market.fix142.enabled=false', '--shared-market.fix142.acknowledgment-enabled=false',
        '--trading.scheduled-analysis-enabled=false',
        ('--local-candle.instance-lock-path="' + $InstanceLockPath + '"'))
    # Give the background JVM its own empty stdin instead of Jenkins input.
    $stdinFile = Join-Path $release 'stdin.empty'
    [IO.File]::WriteAllText($stdinFile, '')
    $child = Start-Process -FilePath $JavaExe -ArgumentList $arguments -WorkingDirectory $DeployDirectory `
        -RedirectStandardInput $stdinFile -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $record = [ordered]@{ processId=$child.Id; startTimeUtc=$child.StartTime.ToUniversalTime().ToString('o');
        startTimeKsa=([DateTimeOffset]$child.StartTime.ToUniversalTime()).ToOffset([TimeSpan]::FromHours(3)).ToString('o');
        java=$JavaExe; jar=$releaseJar; sha256=$buildHash; stdout=$stdout; stderr=$stderr; mode='OFF';
        fix142Enabled=$false; fix142AcknowledgmentEnabled=$false; scheduledAnalysisEnabled=$false;
        instanceLockPath=$InstanceLockPath; exclusionScope='SAME_HOST_SAME_PATH'; distributedFencing=$false }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $release 'process.json') -Encoding UTF8
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'process.json') -Encoding UTF8
    Write-Host "[FIX-135][START] PID=$($child.Id) JAR=$releaseJar SHA256=$buildHash mode=OFF lockPath=$InstanceLockPath"
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
        # FIX-153: observe the OFF instance guard as well as the generic Boot marker.
        $localInstance = Select-String -LiteralPath $stdout -Pattern '\[FIX-153\]\[LOCAL_INSTANCE_ACQUIRED\].*mode=OFF' -Quiet
        if ($listeners.Count -gt 0 -and $started -and $localInstance) { $stable++ } else { $stable = 0 }
        # Three samples five seconds apart: same launched process + startup marker + OFF guard marker.
        if ($stable -ge 3) {
            Write-Host "[FIX-135][STARTUP_VERIFIED] PID=$($child.Id); mode=OFF. Process/port startup and OFF guard observed; this does not verify local ingestion, protection, analysis or trade acceptance."
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
    Write-Host '[DEPLOY][CLEANUP_START]'
    try {
        # Keep full logs in the immutable release; archive only bounded tails.
        foreach ($log in @($stdout,$stderr)) {
            if ($log -and (Test-Path -LiteralPath $log)) {
                try {
                    $snapshot = Join-Path $evidence ([IO.Path]::GetFileName($log))
                    Get-Content -LiteralPath $log -Tail 200 |
                        Set-Content -LiteralPath $snapshot -Encoding UTF8
                } catch {
                    Write-Warning "Could not archive log tail: $($_.Exception.Message)"
                }
            }
        }
    } finally {
        if ($ownsMutex) { $mutex.ReleaseMutex() }
        if ($null -ne $mutex) { $mutex.Dispose() }
    }
    Write-Host '[DEPLOY][CLEANUP_END]'
}

# Reached only after successful startup and cleanup; catch exits with code 1.
Write-Host '[DEPLOY][SCRIPT_COMPLETE] Startup verified; returning success to Jenkins.'
exit 0
