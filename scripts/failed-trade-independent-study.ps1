# Isolated dual-track failed-trade study. Iterations 1-8 are independent.
# Coordinator never shares one agent's findings with the other.
# Agents never receive AGENT_DB_PASSWORD; they emit DB_REQUEST blocks.
param(
  [string]$ProjectRoot = (Get-Location).Path,
  [int]$ProcessTimeoutSeconds = 1800,
  [ValidateSet('low','medium','high','max')][string]$ClaudeEffort = 'medium'
)
$ErrorActionPreference='Stop'
if($ProcessTimeoutSeconds -lt 1){ throw 'ProcessTimeoutSeconds must be >= 1' }
$root=(Resolve-Path $ProjectRoot).Path
$docs=Join-Path $root 'agent-docs'
$runId=Get-Date -Format 'yyyyMMdd-HHmmss'
$run=Join-Path $docs "runs\$runId"
$codexPriv=Join-Path $run 'private\CODEX'
$claudePriv=Join-Path $run 'private\CLAUDE'
$utf8=New-Object System.Text.UTF8Encoding $true
New-Item -ItemType Directory -Force -Path $run,$codexPriv,$claudePriv,(Join-Path $run 'db\CODEX'),(Join-Path $run 'db\CLAUDE') | Out-Null
$log=Join-Path $run 'PROGRESS.log'
$liveStatusPath=Join-Path $docs 'LIVE_TEAM_STATUS.md'
$liveEventsPath=Join-Path $run 'LIVE_EVENTS.log'
$charterSrc=Join-Path $docs 'FAILURE_TRADE_INDEPENDENT_COMPARATIVE_STUDY.md'
if(-not (Test-Path -LiteralPath $charterSrc)){ throw "Charter missing: $charterSrc" }
Copy-Item -LiteralPath $charterSrc -Destination (Join-Path $run 'CHARTER.md') -Force
$script:liveStart=Get-Date
$script:livePhase='INDEPENDENT STUDY'
$script:liveRound=1
$script:codexActivity='Idle'
$script:claudeActivity='Idle'
$script:codexIter=0
$script:claudeIter=0
function Say([string]$m){
  $line='['+(Get-Date -Format 'HH:mm:ss')+'] '+$m
  Write-Host $line
  Add-Content -LiteralPath $log -Value $line -Encoding UTF8
}
function Write-LiveEvent([string]$Agent,[string]$Type,[string]$Message){
  $safeType=($Type.ToUpperInvariant() -replace '[^A-Z_]','_')
  $line='['+(Get-Date -Format 'HH:mm:ss')+'] ['+$Agent+'] ['+$safeType+'] '+$Message
  Write-Host $line
  Add-Content -LiteralPath $liveEventsPath -Value $line -Encoding UTF8
}
function Update-LiveStatus {
  param([string]$Next='Continue independent tracks.')
  $elapsed=(Get-Date)-$script:liveStart
  $body=@"
# LIVE TEAM STATUS

Investigation: FAILURE TRADE INDEPENDENT COMPARATIVE STUDY (analysis-only; no implementation)
Origin: COORDINATOR
Handoff type: INVESTIGATION
Run: $runId
Current phase: $script:livePhase
Current round: $script:liveRound
Elapsed: $([math]::Floor($elapsed.TotalMinutes))m $($elapsed.Seconds)s
Consensus status: INDEPENDENT TRACKS (NO CONSENSUS ATTEMPT)

## Current Activity
- CODEX: $script:codexActivity
- CLAUDE: $script:claudeActivity

## Current Common Ground
Tracks are isolated until both Iteration 8 reports are frozen. No comparison yet.

## Open Disagreements
Not applicable during Iterations 1-8.

## Evidence Collected
Private per-agent artifacts only. Do not share across tracks.
Codex last completed iteration: $script:codexIter
Claude last completed iteration: $script:claudeIter

## Current Working Hypotheses
Withheld from the cross-track dashboard until freeze.

## Risks / Unknowns
Charter section 26 was truncated in the original request; sections 1-25 are the contract.
Agents must not read the other private folder, this status file, or LIVE_EVENTS.log.

## Next Actions
$Next

Live chronological events: agent-docs/runs/$runId/LIVE_EVENTS.log
Raw transcripts remain authoritative audit artifacts for agent outputs.
This dashboard exposes work summaries/evidence, never private chain-of-thought.
"@
  [IO.File]::WriteAllText($liveStatusPath,$body,$utf8)
}
function LatestBinary($pattern,$relative){
  $ext=Join-Path $env:USERPROFILE '.cursor\extensions'
  $d=Get-ChildItem $ext -Directory | Where-Object Name -Like $pattern | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if(-not $d){ throw "Extension not found: $pattern" }
  $exe=Join-Path $d.FullName $relative
  if(-not (Test-Path $exe)){ throw "Executable missing: $exe" }
  return $exe
}
function ConvertTo-ProcessArgumentString([string[]]$ArgumentList){
  $parts=@()
  foreach($a in $ArgumentList){
    if($null -eq $a){ continue }
    if($a -eq ''){ $parts += '""'; continue }
    if($a -notmatch '[ \t"]'){ $parts += $a; continue }
    $escaped=[regex]::Replace($a, '(\\*)"','$1$1\"')
    $escaped=[regex]::Replace($escaped, '(\\+)$','$1$1')
    $parts += '"' + $escaped + '"'
  }
  return ($parts -join ' ')
}
function Invoke-NativeCapturedProcess {
  param(
    [Parameter(Mandatory=$true)][string]$FileName,
    [Parameter(Mandatory=$true)][string[]]$ArgumentList,
    [Parameter(Mandatory=$true)][string]$WorkingDirectory,
    [Parameter(Mandatory=$true)][string]$StdoutPath,
    [Parameter(Mandatory=$true)][string]$StderrPath,
    [string]$StdinText,
    [Parameter(Mandatory=$true)][int]$TimeoutMs,
    [scriptblock]$Heartbeat
  )
  $enc=New-Object System.Text.UTF8Encoding $false
  $psi=New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName=$FileName
  $psi.Arguments=(ConvertTo-ProcessArgumentString $ArgumentList)
  $psi.WorkingDirectory=$WorkingDirectory
  $psi.UseShellExecute=$false
  $psi.RedirectStandardInput=$true
  $psi.RedirectStandardOutput=$true
  $psi.RedirectStandardError=$true
  $psi.CreateNoWindow=$true
  $psi.StandardOutputEncoding=$enc
  $psi.StandardErrorEncoding=$enc
  if($psi.EnvironmentVariables.ContainsKey('AGENT_DB_PASSWORD')){ [void]$psi.EnvironmentVariables.Remove('AGENT_DB_PASSWORD') }
  if($psi.EnvironmentVariables.ContainsKey('MYSQL_PWD')){ [void]$psi.EnvironmentVariables.Remove('MYSQL_PWD') }
  $p=New-Object System.Diagnostics.Process
  $p.StartInfo=$psi
  $stdout=''; $stderr=''; $timedOut=$false
  try {
    if(-not $p.Start()){ throw "Failed to start process: $FileName" }
    $outTask=$p.StandardOutput.ReadToEndAsync()
    $errTask=$p.StandardError.ReadToEndAsync()
    $stdinWriter=New-Object System.IO.StreamWriter($p.StandardInput.BaseStream, $enc, 1024, $true)
    $stdinWriter.NewLine="`n"
    try {
      if($null -ne $StdinText -and $StdinText -ne ''){ $stdinWriter.Write($StdinText); $stdinWriter.Flush() }
    } finally { $stdinWriter.Dispose(); $p.StandardInput.Close() }
    $sw=[Diagnostics.Stopwatch]::StartNew()
    $nextHeartbeat=15
    while(-not $p.HasExited -and $sw.ElapsedMilliseconds -lt $TimeoutMs){
      [void]$p.WaitForExit(1000)
      if($Heartbeat -and $sw.Elapsed.TotalSeconds -ge $nextHeartbeat){
        & $Heartbeat ([int]$sw.Elapsed.TotalSeconds)
        $nextHeartbeat += 15
      }
    }
    if(-not $p.HasExited){
      $timedOut=$true
      try { $p.Kill() } catch {}
      try { $p.WaitForExit() } catch {}
    }
    $sw.Stop()
    try { $stdout=$outTask.GetAwaiter().GetResult() } catch { $stdout='' }
    try { $stderr=$errTask.GetAwaiter().GetResult() } catch { $stderr='' }
    if($null -eq $stdout){ $stdout='' }
    if($null -eq $stderr){ $stderr='' }
    if($timedOut){ $stderr=('TIMEOUT after {0} ms. Process killed.' -f $TimeoutMs) + "`r`n" + $stderr }
    [IO.File]::WriteAllText($StdoutPath, $stdout, $enc)
    [IO.File]::WriteAllText($StderrPath, $stderr, $enc)
    if($timedOut){ return @{ ExitCode=$null; TimedOut=$true; Stdout=$stdout; Stderr=$stderr } }
    return @{ ExitCode=[int]$p.ExitCode; TimedOut=$false; Stdout=$stdout; Stderr=$stderr }
  } finally { $p.Dispose() }
}
function Get-IterationTitle([int]$n){
  switch($n){
    1 { 'ITERATION 1 - TRADE IDENTIFICATION AND RECONSTRUCTION' }
    2 { 'ITERATION 2 - DATABASE FORENSICS' }
    3 { 'ITERATION 3 - LOG AND TIMING FORENSICS' }
    4 { 'ITERATION 4 - CODE AND TRADE ROUTE RECONSTRUCTION' }
    5 { 'ITERATION 5 - ENTRY QUALITY ANALYSIS' }
    6 { 'ITERATION 6 - ROOT CAUSE AND EXTERNAL RESEARCH' }
    7 { 'ITERATION 7 - SELF-CHALLENGE' }
    8 { 'ITERATION 8 - FINAL INDEPENDENT CLASSIFICATION AND FROZEN REPORT' }
    default { "ITERATION $n" }
  }
}
function Build-AgentPrompt([string]$who,[int]$iter,[string]$privDir){
  $other=if($who -eq 'CODEX'){'CLAUDE'}else{'CODEX'}
  $ownDocs=if($who -eq 'CODEX'){'codex-docs/'}else{'claude-docs/'}
  $reportName=if($who -eq 'CODEX'){'CODEX_FAILED_TRADE_STUDY.md'}else{'CLAUDE_FAILED_TRADE_STUDY.md'}
  $title=Get-IterationTitle $iter
  $priorFiles=Get-ChildItem -LiteralPath $privDir -File | Sort-Object Name | ForEach-Object { $_.Name }
  $priorList=if($priorFiles){ ($priorFiles -join ', ') } else { '(none yet; this is your first iteration)' }
  $dbDirRel='agent-docs/runs/'+$runId+'/db/'+$who
  $privRel='agent-docs/runs/'+$runId+'/private/'+$who
  @"
You are $who performing TRACK $(if($who -eq 'CODEX'){'A'}else{'B'}) of a FAILURE TRADE INDEPENDENT COMPARATIVE STUDY.

HARD ISOLATION
- Do NOT read, glob, grep, or mention the other agent's files.
- Forbidden: agent-docs/runs/$runId/private/$other/
- Forbidden: agent-docs/runs/$runId/db/$other/
- Forbidden: agent-docs/LIVE_TEAM_STATUS.md
- Forbidden: agent-docs/runs/$runId/LIVE_EVENTS.log
- Forbidden: agent-docs/memory/$($other)_LATEST.md and the other agent's history files
- Forbidden: PEER_INDEX.md as a source of the other agent's claims
- Do not attempt consensus. Do not argue with $other. You will never see their trades or findings during Iterations 1-8.

READ FIRST
- AGENTS.md
- agent-docs/AGREED_KNOWLEDGE.md
- agent-docs/DECISIONS.md
- agent-docs/DATABASE_INVESTIGATION_RULES.md
- $($ownDocs) including TRADE_INVESTIGATION_AGENT.md when present
- The charter: agent-docs/runs/$runId/CHARTER.md
- Your private prior artifacts only: $privRel ($priorList)
- Your private DB results only: $dbDirRel

ANALYSIS ONLY
No application code changes, trading-rule changes, DB writes, config changes, deploy, git, or implementation.

TIME
Stored timestamps are UTC. Report humans in KSA = UTC+3. Never label raw DB UTC as KSA. Show both UTC and KSA for important times.

WINDOWS
Independently study failed trades around 10:00 KSA and 18:00 KSA.
Calendar context: 2026-10-10. You must independently decide which date(s) and exact tolerance apply from evidence. Document selection methodology.
Target 6 failed trades per window (12 total). If fewer exist, report the actual number; do not silently substitute or expand.

FAILURE
Determine failed trades from project evidence. Negative P/L is not automatically a defect. NORMAL_MARKET_LOSS is a valid primary classification.

DATABASE ACCESS
You do NOT have the database password and MUST NOT try mysql/mysql.exe or AGENT_DB_PASSWORD.
To query crypto_ai, emit one or more blocks exactly like this (read-only SQL only: SELECT/SHOW/DESCRIBE/DESC/EXPLAIN):

### DB_REQUEST
QueryId: DBQ-$who-I$iter-001
Purpose: short purpose
SQL:
SELECT ...
### END_DB_REQUEST

The coordinator will execute these after this iteration and place results under $dbDirRel for your next iteration.
Do not invent query results. If you still need data, request it.

LOGS
You may search repository-local logs with Read/Grep (Claude) or read-only tools (Codex). Record exact file paths, keys, UTC, and KSA.

CURRENT TASK
$title
Follow the charter for this iteration completely.
Iteration $iter of 8. This is a SELF-REVIEW iteration, not a debate.

$(if($iter -eq 8){"ITERATION 8 OUTPUT: Your stdout MUST contain the complete frozen report $reportName using the charter summary fields and the mandatory table columns, plus DATABASE EVIDENCE, LOG EVIDENCE, and CODE EVIDENCE sections. After this freeze you must not change the report."}else{"Output structured sections: FINDINGS, EVIDENCE, UNKNOWNS, NEXT_DB_REQUESTS, SELECTED_TRADE_COUNT (after you have a count)."} )

Label material claims PROJECT EVIDENCE, EXTERNAL RESEARCH, or AGENT INFERENCE.
Public web research is allowed. Do not upload private DB/log/source/secrets.
"@
}
function Invoke-DbRequests([string]$who,[string]$body,[int]$iter){
  $wrap=Join-Path $root 'scripts\agent-db-query.ps1'
  $rx=[regex]'(?ms)### DB_REQUEST\s*(.*?)### END_DB_REQUEST'
  $n=0
  foreach($m in $rx.Matches($body)){
    $block=$m.Groups[1].Value
    $qid='DBQ-'+$who+'-I'+$iter+'-'+('{0:D3}' -f (++$n))
    $qim=[regex]::Match($block,'(?im)^\s*QueryId:\s*(\S+)')
    if($qim.Success){ $qid=$qim.Groups[1].Value.Trim() }
    $purpose='iteration '+$iter+' request'
    $pm=[regex]::Match($block,'(?im)^\s*Purpose:\s*(.+)$')
    if($pm.Success){ $purpose=$pm.Groups[1].Value.Trim() }
    $sm=[regex]::Match($block,'(?is)SQL:\s*(.*)$')
    if(-not $sm.Success){ Write-LiveEvent $who 'DB' "Skipped malformed DB_REQUEST (no SQL) $qid"; continue }
    $sql=$sm.Groups[1].Value.Trim()
    Write-LiveEvent $who 'DB' "Executing $qid (SQL text not logged here)"
    try {
      $isoRun = "$runId-$who"
      & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $wrap -Agent $who -RunId $isoRun -QueryId $qid -Purpose $purpose -ProjectRoot $root -Query $sql | Out-Null
      $destSql=Join-Path $root "agent-docs\runs\$isoRun\db\$qid-$who.sql"
      $destRes=Join-Path $root "agent-docs\runs\$isoRun\db\$qid-$who-result.txt"
      $privDb=Join-Path $run "db\$who"
      if(Test-Path -LiteralPath $destSql){ Copy-Item $destSql (Join-Path $privDb (Split-Path $destSql -Leaf)) -Force }
      if(Test-Path -LiteralPath $destRes){ Copy-Item $destRes (Join-Path $privDb (Split-Path $destRes -Leaf)) -Force }
      Write-LiveEvent $who 'DB' "Completed $qid"
    } catch {
      $errFile=Join-Path $run "db\$who\$qid-ERROR.txt"
      [IO.File]::WriteAllText($errFile, [string]$_, (New-Object System.Text.UTF8Encoding $false))
      Write-LiveEvent $who 'DB' "Failed $qid (error stored privately)"
    }
  }
  if($n -eq 0){ Write-LiveEvent $who 'DB' "No DB_REQUEST blocks in iteration $iter" }
}
$codex=LatestBinary 'openai.chatgpt-*' 'bin\windows-x86_64\codex.exe'
$claude=LatestBinary 'anthropic.claude-code-*' 'resources\native-binary\claude.exe'
Update-LiveStatus -Next 'Starting isolated Iteration 1 for both tracks.'
Write-LiveEvent 'COORDINATOR' 'STATUS' "Independent failed-trade study $runId started. Isolation ON. No cross-track sharing."
Say "RUN $runId independent failed-trade study"

for($iter=1; $iter -le 8; $iter++){
  $script:liveRound=$iter
  $script:livePhase='INDEPENDENT ITERATION '+$iter
  foreach($who in @('CODEX','CLAUDE')){
    $priv=if($who -eq 'CODEX'){$codexPriv}else{$claudePriv}
    $task=Get-IterationTitle $iter
    if($who -eq 'CODEX'){ $script:codexActivity=$task } else { $script:claudeActivity=$task }
    Write-LiveEvent $who 'STATUS' "Started $task"
    Update-LiveStatus -Next "$who is working on: $task"
    $prompt=Build-AgentPrompt $who $iter $priv
    $name=('iteration{0:D2}-{1}' -f $iter, $who.ToLowerInvariant())
    $out=Join-Path $priv "$name.txt"
    $err=Join-Path $priv "$name.stderr.txt"
    $promptPath=Join-Path $priv "$name.prompt.txt"
    [IO.File]::WriteAllText($promptPath,$prompt,(New-Object System.Text.UTF8Encoding $false))
    Say "START $who / $name"
    $timeoutMs=$ProcessTimeoutSeconds*1000
    if($who -eq 'CODEX'){
      $result=Invoke-NativeCapturedProcess -FileName $codex -ArgumentList @('--search','exec','--sandbox','read-only','-c','model_reasoning_effort=high','-C',$root,'-') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) $msg=('Still working - elapsed {0}m {1}s; task: {2}' -f [int]($sec/60),($sec % 60),$task); Write-LiveEvent 'CODEX' 'STATUS' $msg; Update-LiveStatus -Next ('CODEX continues: '+$task) }
    } else {
      $result=Invoke-NativeCapturedProcess -FileName $claude -ArgumentList @('-p','--permission-mode','plan','--permission-prompts','none','--effort',$ClaudeEffort,'--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) $msg=('Still working - elapsed {0}m {1}s; task: {2}' -f [int]($sec/60),($sec % 60),$task); Write-LiveEvent 'CLAUDE' 'STATUS' $msg; Update-LiveStatus -Next ('CLAUDE continues: '+$task) }
    }
    if($result.TimedOut){ throw "TIMEOUT: $who / $name" }
    if($result.ExitCode -ne 0){ throw "$who failed ExitCode=$($result.ExitCode). See $err" }
    if([string]::IsNullOrWhiteSpace($result.Stdout)){ throw "$who returned empty output. See $err" }
    Say "DONE $who / $name"
    if($who -eq 'CODEX'){ $script:codexIter=$iter; $script:codexActivity="Completed $task" } else { $script:claudeIter=$iter; $script:claudeActivity="Completed $task" }
    Write-LiveEvent $who 'STATUS' "Completed $task"
    Invoke-DbRequests $who $result.Stdout $iter
    if($iter -eq 8){
      $finalName=if($who -eq 'CODEX'){'CODEX_FAILED_TRADE_STUDY.md'}else{'CLAUDE_FAILED_TRADE_STUDY.md'}
      [IO.File]::WriteAllText((Join-Path $priv $finalName), $result.Stdout, (New-Object System.Text.UTF8Encoding $false))
      Write-LiveEvent $who 'STATUS' "Frozen private report $finalName"
    }
    Update-LiveStatus -Next "Completed $who iteration $iter"
  }
}

Copy-Item (Join-Path $codexPriv 'CODEX_FAILED_TRADE_STUDY.md') (Join-Path $run 'CODEX_FAILED_TRADE_STUDY.md') -Force
Copy-Item (Join-Path $claudePriv 'CLAUDE_FAILED_TRADE_STUDY.md') (Join-Path $run 'CLAUDE_FAILED_TRADE_STUDY.md') -Force
Write-LiveEvent 'COORDINATOR' 'STATUS' 'Both Iteration 8 reports frozen. Comparison may begin in a later coordinator step.'
$script:livePhase='FROZEN - COMPARISON PENDING'
Update-LiveStatus -Next 'Both reports frozen. Coordinator comparison is a separate step and was not started automatically.'
Say 'FROZEN both independent reports. Comparison not auto-started.'
exit 0
