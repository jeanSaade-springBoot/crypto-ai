# Daily failed-trade forensic study launcher. Analysis-only. No scheduled-task install here.
param(
  [Parameter(Mandatory=$true)][ValidateSet('WINDOW1','WINDOW2')][string]$Window,
  [switch]$TestMode,
  [string]$ProjectRoot=(Get-Location).Path,
  [int]$ProcessTimeoutSeconds=1800,
  [ValidateSet('low','medium','high','max')][string]$ClaudeEffort='medium'
)
$ErrorActionPreference='Stop'
if($ProcessTimeoutSeconds -lt 1){ throw 'ProcessTimeoutSeconds must be >= 1' }
if($TestMode){ $ProcessTimeoutSeconds=[Math]::Min($ProcessTimeoutSeconds,300) }
if([string]::IsNullOrWhiteSpace($env:AGENT_DB_PASSWORD)){
  $env:AGENT_DB_PASSWORD=[Environment]::GetEnvironmentVariable('AGENT_DB_PASSWORD','User')
}
$root=(Resolve-Path $ProjectRoot).Path
$docs=Join-Path $root 'agent-docs'
$studies=Join-Path $docs 'failed-trade-studies'
$lockPath=Join-Path $studies 'study.lock'
$queuePath=Join-Path $studies 'queue.txt'
New-Item -ItemType Directory -Force -Path $studies | Out-Null
$ksaNow=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId((Get-Date).ToUniversalTime(),'Arab Standard Time')
$dateStamp=$ksaNow.ToString('dd-MM-yy')
$outDir=Join-Path $studies $dateStamp
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$suffix=''
if($TestMode){ $suffix='-TEST' }
$runId='fts-'+$Window+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+$suffix
$run=Join-Path $docs "runs\$runId"
$codexPriv=Join-Path $run 'private\CODEX'
$claudePriv=Join-Path $run 'private\CLAUDE'
$utf8bom=New-Object System.Text.UTF8Encoding $true
$utf8=New-Object System.Text.UTF8Encoding $false
New-Item -ItemType Directory -Force -Path $run,$codexPriv,$claudePriv,(Join-Path $run 'db\CODEX'),(Join-Path $run 'db\CLAUDE') | Out-Null
$log=Join-Path $run 'PROGRESS.log'
$liveStatusPath=Join-Path $docs 'LIVE_TEAM_STATUS.md'
$liveEventsPath=Join-Path $run 'LIVE_EVENTS.log'
$script:liveStart=Get-Date
$script:livePhase='DAILY FAILED-TRADE STUDY'
$script:liveRound=0
$script:codexActivity='Idle'
$script:claudeActivity='Idle'
$script:windowLabel=$Window
$script:testMode=[bool]$TestMode
$lockStream=$null

function Say([string]$m){
  $line='['+(Get-Date -Format 'HH:mm:ss')+'] '+$m
  Write-Host $line
  Add-Content -LiteralPath $log -Value $line -Encoding UTF8
}
function Write-LiveEvent([string]$Agent,[string]$Type,[string]$Message){
  $line='['+(Get-Date -Format 'HH:mm:ss')+'] ['+$Agent+'] ['+(($Type.ToUpperInvariant() -replace '[^A-Z_]','_'))+'] '+$Message
  Write-Host $line
  Add-Content -LiteralPath $liveEventsPath -Value $line -Encoding UTF8
}
function Update-LiveStatus([string]$Next='Continue.'){
  $elapsed=(Get-Date)-$script:liveStart
  $mode=if($script:testMode){'UNATTENDED TEST MODE - not a full 8-iteration forensic report'}else{'PRODUCTION WINDOW RUN'}
  $body=@"
# LIVE TEAM STATUS

Investigation: DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY ($mode)
Origin: COORDINATOR
Handoff type: INVESTIGATION
Run: $runId
Current phase: $script:livePhase
Current round: $script:liveRound
Elapsed: $([math]::Floor($elapsed.TotalMinutes))m $($elapsed.Seconds)s
Consensus status: INDEPENDENT TRACKS (NO CONSENSUS ATTEMPT DURING ITERATIONS)

## Current Activity
- CODEX: $script:codexActivity
- CLAUDE: $script:claudeActivity

## Current Common Ground
Shared frozen trade IDs are INPUT DATA only. Findings remain isolated until both reports freeze.

## Open Disagreements
Not applicable until post-freeze comparison.

## Evidence Collected
Window: $script:windowLabel
Private per-agent artifacts under agent-docs/runs/$runId/private/

## Current Working Hypotheses
Withheld from cross-track prompts.

## Risks / Unknowns
TestMode does not replace a full 8-iteration study.

## Next Actions
$Next

Live chronological events: agent-docs/runs/$runId/LIVE_EVENTS.log
Raw transcripts remain authoritative audit artifacts for agent outputs.
This dashboard exposes work summaries/evidence, never private chain-of-thought.
"@
  [IO.File]::WriteAllText($liveStatusPath,$body,$utf8bom)
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
    $escaped=[regex]::Replace($a,'(\\*)"','$1$1\"')
    $escaped=[regex]::Replace($escaped,'(\\+)$','$1$1')
    $parts += '"'+$escaped+'"'
  }
  return ($parts -join ' ')
}
function Invoke-NativeCapturedProcess {
  param([string]$FileName,[string[]]$ArgumentList,[string]$WorkingDirectory,[string]$StdoutPath,[string]$StderrPath,[string]$StdinText,[int]$TimeoutMs,[scriptblock]$Heartbeat)
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
  try {
    if(-not $p.Start()){ throw "Failed to start $FileName" }
    $outTask=$p.StandardOutput.ReadToEndAsync()
    $errTask=$p.StandardError.ReadToEndAsync()
    $stdinWriter=New-Object System.IO.StreamWriter($p.StandardInput.BaseStream,$enc,1024,$true)
    $stdinWriter.NewLine="`n"
    try { if($StdinText){ $stdinWriter.Write($StdinText); $stdinWriter.Flush() } } finally { $stdinWriter.Dispose(); $p.StandardInput.Close() }
    $sw=[Diagnostics.Stopwatch]::StartNew(); $next=15; $timedOut=$false
    while(-not $p.HasExited -and $sw.ElapsedMilliseconds -lt $TimeoutMs){
      [void]$p.WaitForExit(1000)
      if($Heartbeat -and $sw.Elapsed.TotalSeconds -ge $next){ & $Heartbeat ([int]$sw.Elapsed.TotalSeconds); $next += 15 }
    }
    if(-not $p.HasExited){ $timedOut=$true; try{$p.Kill()}catch{}; try{$p.WaitForExit()}catch{} }
    $stdout=''; $stderr=''
    try{$stdout=$outTask.GetAwaiter().GetResult()}catch{}
    try{$stderr=$errTask.GetAwaiter().GetResult()}catch{}
    if($null -eq $stdout){$stdout=''}
    if($null -eq $stderr){$stderr=''}
    if($timedOut){ $stderr=('TIMEOUT after {0} ms' -f $TimeoutMs)+"`r`n"+$stderr }
    [IO.File]::WriteAllText($StdoutPath,$stdout,$enc)
    [IO.File]::WriteAllText($StderrPath,$stderr,$enc)
    if($timedOut){ return @{ExitCode=$null;TimedOut=$true;Stdout=$stdout;Stderr=$stderr} }
    return @{ExitCode=[int]$p.ExitCode;TimedOut=$false;Stdout=$stdout;Stderr=$stderr}
  } finally { $p.Dispose() }
}
function Invoke-Db([string]$QueryId,[string]$Purpose,[string]$Query){
  $wrap=Join-Path $root 'scripts\agent-db-query.ps1'
  return (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $wrap -Agent COORDINATOR -RunId $runId -QueryId $QueryId -Purpose $Purpose -ProjectRoot $root -Query $Query)
}
function Enter-StudyLock {
  if(Test-Path -LiteralPath $lockPath){
    $old=[IO.File]::ReadAllText($lockPath)
    $pidMatch=[regex]::Match($old,'(?im)^PID=(\d+)')
    $alive=$false
    if($pidMatch.Success){
      $alive=[bool](Get-Process -Id ([int]$pidMatch.Groups[1].Value) -ErrorAction SilentlyContinue)
    }
    if($alive){
      if($Window -eq 'WINDOW2'){
        $q=@(
          'WINDOW=WINDOW2',
          ('QUEUED_AT_KSA='+$ksaNow.ToString('yyyy-MM-dd HH:mm:ss')),
          'INTENDED_SNAPSHOT_KSA='+$ksaNow.ToString('yyyy-MM-dd')+' 18:00:00',
          'REASON=WINDOW1_OR_OTHER_STUDY_STILL_ACTIVE',
          'SNAPSHOT_SEMANTICS=NOT_PRESERVED_IF_STARTED_LATE'
        ) -join "`r`n"
        [IO.File]::WriteAllText($queuePath,$q,$utf8)
        throw "OVERLAP: another study holds $lockPath. WINDOW2 queued at $queuePath. Exact 18:00 snapshot cannot be taken while blocked."
      }
      throw "OVERLAP: study lock held. Refusing to start $Window concurrently."
    }
  }
  $lockStream=[IO.File]::Open($lockPath,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::Read)
  $bytes=$utf8.GetBytes("PID=$PID`r`nWINDOW=$Window`r`nRUN=$runId`r`nSTARTED_KSA=$($ksaNow.ToString('o'))`r`n")
  $lockStream.Write($bytes,0,$bytes.Length)
  $lockStream.Flush()
}
function Exit-StudyLock {
  if($lockStream){ try{$lockStream.Dispose()}catch{} }
  if(Test-Path -LiteralPath $lockPath){ Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue }
}

try {
Enter-StudyLock
Update-LiveStatus -Next "Proving authoritative completed Production failing-trade entity."
Write-LiveEvent 'COORDINATOR' 'STATUS' "Launcher started Window=$Window TestMode=$TestMode Run=$runId"
Say "START $Window TestMode=$TestMode"

$desc=Invoke-Db 'DBQ-FTS-001' 'Prove paper_position schema' 'DESCRIBE paper_position'
$st=Invoke-Db 'DBQ-FTS-002' 'Prove paper_position status values' "SELECT status, COUNT(*) n, SUM(closed_at IS NOT NULL) with_close, SUM(realized_pnl < 0) neg_pnl FROM paper_position GROUP BY status"
$wt=Invoke-Db 'DBQ-FTS-003' 'Compare wallet_trade SELL pnl (not used as primary if position lifecycle exists)' "SELECT status, COUNT(*) n, SUM(realized_pnl_usdt < 0) neg_pnl FROM wallet_trade GROUP BY status"
$rt=Invoke-Db 'DBQ-FTS-004' 'Confirm Replay shadow table is separate' "SHOW TABLES LIKE 'wallet_position_test'"
$selSql=@"
SELECT id, symbol, status, realized_pnl, opened_at, closed_at, close_reason, signal_id
FROM paper_position
WHERE status='CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6
"@
$sel=Invoke-Db 'DBQ-FTS-005' 'Latest 6 completed failing Production paper positions by closed_at' $selSql
Write-LiveEvent 'COORDINATOR' 'EVIDENCE' 'Authoritative entity: paper_position CLOSED with closed_at and realized_pnl<0. Replay table wallet_position_test excluded. Ranking by closed_at not opened_at/signal.'
$lines=@($sel -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^id\t' })
if($lines.Count -lt 1){ throw 'No qualifying completed failing paper_position rows.' }
$tradeIds=@(); $tradeRows=@()
foreach($line in $lines){
  $c=$line -split "`t"
  $tradeIds += $c[0]
  $tradeRows += $line
}
$idsText=($tradeIds | ForEach-Object { $i=1 } { '{0}. {1}' -f $i, $_; $i++ }) -join "`n"
$snapUtc=(Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss')
$snapKsa=$ksaNow.ToString('yyyy-MM-dd HH:mm:ss')
$intended=$ksaNow.ToString('yyyy-MM-dd')+' 18:00:00'
if($Window -eq 'WINDOW1'){ $intended=$ksaNow.ToString('yyyy-MM-dd')+' 10:00:00' }
$expectHm='18:00'
if($Window -eq 'WINDOW1'){ $expectHm='10:00' }
if($TestMode){
  $snapNote="TEST MODE: snapshot is launcher start, not the scheduled $intended KSA clock."
} elseif($ksaNow.ToString('HH:mm') -eq $expectHm){
  $snapNote='On-schedule snapshot.'
} else {
  $snapNote="SNAPSHOT CLOCK DRIFT: intended $intended KSA; actual $snapKsa KSA. Exact window clock was not preserved."
}
$freeze=@"
STUDY WINDOW: $Window
SNAPSHOT TIME UTC: $snapUtc
SNAPSHOT TIME KSA: $snapKsa
INTENDED WINDOW CLOCK KSA: $intended
SNAPSHOT NOTE: $snapNote
AUTHORITATIVE ENTITY: crypto_ai.paper_position
SELECTION: status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6
EXCLUDED: OPEN/incomplete rows; NULL closed_at; non-negative realized_pnl; wallet_position_test (Replay)

SELECTED TRADE IDS:
$idsText

PROOF QUERIES: DBQ-FTS-001 .. DBQ-FTS-005
"@
[IO.File]::WriteAllText((Join-Path $run 'FROZEN_TRADE_SELECTION.md'),$freeze,$utf8)
[IO.File]::WriteAllText((Join-Path $outDir ("frozen-selection-$Window.md")),$freeze,$utf8)
Write-LiveEvent 'COORDINATOR' 'STATUS' ("Frozen $($tradeIds.Count) trade IDs for both agents")
Say "FROZEN $($tradeIds.Count) ids"

$codex=LatestBinary 'openai.chatgpt-*' 'bin\windows-x86_64\codex.exe'
$claude=LatestBinary 'anthropic.claude-code-*' 'resources\native-binary\claude.exe'
$sharedInput=@"
FROZEN INPUT (not a finding): You MUST analyze exactly these paper_position.id values and MUST NOT substitute others:
$($tradeIds -join ', ')
$freeze
"@

function Invoke-AgentShort([string]$who,[string]$prompt,[string]$name,[string]$priv){
  $out=Join-Path $priv "$name.txt"
  $err=Join-Path $priv "$name.stderr.txt"
  [IO.File]::WriteAllText((Join-Path $priv "$name.prompt.txt"),$prompt,$utf8)
  $timeoutMs=$ProcessTimeoutSeconds*1000
  $task='unattended simulation start'
  if($who -eq 'CODEX'){ $script:codexActivity=$task } else { $script:claudeActivity=$task }
  Write-LiveEvent $who 'STATUS' "Started $task"
  Update-LiveStatus -Next "$who $task"
  if($who -eq 'CODEX'){
    $result=Invoke-NativeCapturedProcess -FileName $codex -ArgumentList @('--search','exec','--sandbox','read-only','-c','model_reasoning_effort=medium','-C',$root,'-') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) Write-LiveEvent 'CODEX' 'STATUS' ('Still working - elapsed {0}m {1}s; task: unattended simulation' -f [int]($sec/60),($sec % 60)); Update-LiveStatus -Next 'CODEX continues unattended simulation' }
  } else {
    $result=Invoke-NativeCapturedProcess -FileName $claude -ArgumentList @('-p','--permission-mode','plan','--permission-prompts','none','--effort',$ClaudeEffort,'--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) Write-LiveEvent 'CLAUDE' 'STATUS' ('Still working - elapsed {0}m {1}s; task: unattended simulation' -f [int]($sec/60),($sec % 60)); Update-LiveStatus -Next 'CLAUDE continues unattended simulation' }
  }
  if($result.TimedOut){ throw "TIMEOUT $who simulation" }
  if($result.ExitCode -ne 0){ throw "$who failed ExitCode=$($result.ExitCode)" }
  if([string]::IsNullOrWhiteSpace($result.Stdout)){ throw "$who empty stdout" }
  Write-LiveEvent $who 'STATUS' "Completed $task"
  if($who -eq 'CODEX'){ $script:codexActivity="Completed $task" } else { $script:claudeActivity="Completed $task" }
  return $result.Stdout
}

$basePolicy=@"
You are running an UNATTENDED READ-ONLY simulation of the daily failed-trade study.
No application/config/DB/git/deploy changes. Do not implement fixes.
Do not read the other agent's private folder, LIVE_TEAM_STATUS.md, or LIVE_EVENTS.log.
Do not request interactive confirmation. Internet research is allowed. Do not upload private data.
$sharedInput
"@

if($TestMode){
  $script:livePhase='UNATTENDED TEST'
  $script:liveRound=0
  $codexPrompt=$basePolicy+@"

You are CODEX. Reply with exactly these headings and nothing that requires a human:
SIMULATION_STATUS: STARTED
AUTH: whether Codex CLI authentication appears usable
WEB: one public source (title, URL) about losing-trade / post-trade analysis, labeled EXTERNAL RESEARCH
FROZEN_IDS: repeat the six IDs
DB: state that you did not receive a database password
VERDICT: SIMULATION_OK
"@
  $claudePrompt=$basePolicy+@"

You are CLAUDE. Reply with exactly these headings and nothing that requires a human:
SIMULATION_STATUS: STARTED
AUTH: whether Claude CLI authentication appears usable
WEB: one public source (title, URL) about losing-trade / post-trade analysis, labeled EXTERNAL RESEARCH
FROZEN_IDS: repeat the six IDs
DB: state that you did not receive a database password
VERDICT: SIMULATION_OK
"@
  $codexOut=Invoke-AgentShort 'CODEX' $codexPrompt 'simulation-codex' $codexPriv
  $claudeOut=Invoke-AgentShort 'CLAUDE' $claudePrompt 'simulation-claude' $claudePriv
  $wtag=if($Window -eq 'WINDOW1'){'window1'}else{'window2'}
  $mdPath=Join-Path $outDir ("failedtrade-$wtag-$dateStamp.md")
  $pdfPath=Join-Path $outDir ("failedtrade-$wtag-$dateStamp.pdf")
  $combined=@"
# DAILY FAILED-TRADE STUDY ($Window) - UNATTENDED SIMULATION

**NOT a full 8-iteration forensic report.** Implementation authorization: NOT AUTHORIZED.
Date folder: $dateStamp
Run: $runId

$freeze

## SECTION 1 - CODEX ANALYSIS

$codexOut

## SECTION 2 - CLAUDE ANALYSIS

$claudeOut

## SECTION 3 - COORDINATOR COMMON FINDINGS

Comparison of root causes is withheld: this run is a launcher simulation, not Iteration 8 freeze.
Common input: the same frozen paper_position IDs.
Codex simulation started: see SIMULATION_STATUS in section 1.
Claude simulation started: see SIMULATION_STATUS in section 2.
Internet research requested from both agents.
Database selection used ai_investigator via scripts/agent-db-query.ps1; password not written to this file.

Required full-study table columns will appear in a non-TestMode window run after schedule approval.
"@
  [IO.File]::WriteAllText($mdPath,$combined,$utf8)
  $freezeScript=Join-Path $root 'scripts\freeze-agent-artifact.ps1'
  $pdfOk=$false
  try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $freezeScript -Source $mdPath -Revision 'SIM' -Codex UNKNOWN -Claude UNKNOWN -Authorization 'NOT AUTHORIZED' -Output $pdfPath
    $pdfOk=Test-Path -LiteralPath $pdfPath
  } catch { Write-LiveEvent 'COORDINATOR' 'STATUS' "PDF freeze failed: $($_.Exception.Message)" }
  $script:livePhase='SIMULATION COMPLETE'
  Update-LiveStatus -Next 'STOP for Jean approval. Scheduled tasks were NOT created.'
  Write-LiveEvent 'COORDINATOR' 'STATUS' "Simulation outputs md=$mdPath pdfOk=$pdfOk"
  Say "SIMULATION_COMPLETE md=$mdPath pdfOk=$pdfOk"
  Write-Host 'TASK_SCHEDULER_NOT_CREATED'
  exit 0
}

function Get-IterationTitle([int]$n){
  switch($n){
    1 { 'ITERATION 1 - TRADE RECONSTRUCTION' }
    2 { 'ITERATION 2 - DATABASE FORENSICS' }
    3 { 'ITERATION 3 - LOG AND TIMING FORENSICS' }
    4 { 'ITERATION 4 - CODE / TRADE ROUTE' }
    5 { 'ITERATION 5 - ENTRY QUALITY' }
    6 { 'ITERATION 6 - ROOT CAUSE + RESEARCH' }
    7 { 'ITERATION 7 - SELF-CHALLENGE' }
    8 { 'ITERATION 8 - FINAL CLASSIFICATION' }
    default { "ITERATION $n" }
  }
}
function Invoke-DbRequests([string]$who,[string]$body,[int]$iter){
  $wrap=Join-Path $root 'scripts\agent-db-query.ps1'
  $rx=[regex]'(?ms)### DB_REQUEST\s*(.*?)### END_DB_REQUEST'
  $n=0
  foreach($m in $rx.Matches($body)){
    $block=$m.Groups[1].Value
    $n=$n+1
    $qid='DBQ-'+$who+'-I'+$iter+'-'+('{0:D3}' -f $n)
    $qim=[regex]::Match($block,'(?im)^\s*QueryId:\s*(\S+)')
    if($qim.Success){ $qid=$qim.Groups[1].Value.Trim() }
    $purpose='iteration '+$iter+' request'
    $pm=[regex]::Match($block,'(?im)^\s*Purpose:\s*(.+)$')
    if($pm.Success){ $purpose=$pm.Groups[1].Value.Trim() }
    $sm=[regex]::Match($block,'(?is)SQL:\s*(.*)$')
    if(-not $sm.Success){ Write-LiveEvent $who 'DB' "Skipped malformed DB_REQUEST $qid"; continue }
    $sql=$sm.Groups[1].Value.Trim()
    Write-LiveEvent $who 'DB' "Executing $qid"
    try {
      $isoRun="$runId-$who"
      & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $wrap -Agent $who -RunId $isoRun -QueryId $qid -Purpose $purpose -ProjectRoot $root -Query $sql | Out-Null
      $destSql=Join-Path $root "agent-docs\runs\$isoRun\db\$qid-$who.sql"
      $destRes=Join-Path $root "agent-docs\runs\$isoRun\db\$qid-$who-result.txt"
      $privDb=Join-Path $run "db\$who"
      if(Test-Path -LiteralPath $destSql){ Copy-Item $destSql (Join-Path $privDb (Split-Path $destSql -Leaf)) -Force }
      if(Test-Path -LiteralPath $destRes){ Copy-Item $destRes (Join-Path $privDb (Split-Path $destRes -Leaf)) -Force }
      Write-LiveEvent $who 'DB' "Completed $qid"
    } catch {
      [IO.File]::WriteAllText((Join-Path $run "db\$who\$qid-ERROR.txt"),[string]$_,$utf8)
      Write-LiveEvent $who 'DB' "Failed $qid"
    }
  }
  if($n -eq 0){ Write-LiveEvent $who 'DB' "No DB_REQUEST blocks in iteration $iter" }
}
function Publish-Pdf([string]$mdPath,[string]$pdfPath){
  $pyCandidates=@(
    'C:\Program Files\MySQL\MySQL Workbench 8.0\python.exe',
    'C:\Program Files\MySQL\MySQL Shell 8.0\lib\Python3.13\python.exe'
  )
  $freezeScript=Join-Path $root 'scripts\freeze-agent-artifact.ps1'
  try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $freezeScript -Source $mdPath -Revision 'DAILY' -Codex UNKNOWN -Claude UNKNOWN -Authorization 'NOT AUTHORIZED' -Output $pdfPath
    if(Test-Path -LiteralPath $pdfPath){ return $true }
  } catch {}
  foreach($py in $pyCandidates){
    if(-not (Test-Path -LiteralPath $py)){ continue }
    try {
      & $py -m pip install --quiet reportlab 2>$null | Out-Null
      & $py (Join-Path $root 'scripts\generate-agent-pdf.py') --source $mdPath --output $pdfPath --revision 'DAILY' --codex UNKNOWN --claude UNKNOWN --status 'NOT AUTHORIZED'
      if(Test-Path -LiteralPath $pdfPath){ return $true }
    } catch {}
  }
  return $false
}

$charterDaily=@"
Daily failed-trade independent forensic study. ANALYSIS ONLY. No implementation.
Both agents analyze EXACTLY the frozen paper_position IDs. Do not substitute trades.
8 self-review iterations. No cross-review. Coordinator compares only after both freeze.
"@
[IO.File]::WriteAllText((Join-Path $run 'CHARTER.md'),$charterDaily+$freeze,$utf8)

for($iter=1; $iter -le 8; $iter++){
  $script:liveRound=$iter
  $script:livePhase='INDEPENDENT ITERATION '+$iter
  foreach($who in @('CODEX','CLAUDE')){
    $priv=if($who -eq 'CODEX'){$codexPriv}else{$claudePriv}
    $other='CLAUDE'
    if($who -eq 'CLAUDE'){ $other='CODEX' }
    $ownDocs='codex-docs/'
    if($who -eq 'CLAUDE'){ $ownDocs='claude-docs/' }
    $reportName='CODEX_FAILED_TRADE_STUDY.md'
    if($who -eq 'CLAUDE'){ $reportName='CLAUDE_FAILED_TRADE_STUDY.md' }
    $task=Get-IterationTitle $iter
    if($who -eq 'CODEX'){ $script:codexActivity=$task } else { $script:claudeActivity=$task }
    $priorFiles=Get-ChildItem -LiteralPath $priv -File -ErrorAction SilentlyContinue | Sort-Object Name | ForEach-Object { $_.Name }
    $priorList='(none yet)'
    if($priorFiles){ $priorList=($priorFiles -join ', ') }
    $iter8note='Output FINDINGS, EVIDENCE, UNKNOWNS, and DB_REQUEST blocks as needed.'
    if($iter -eq 8){ $iter8note="ITERATION 8: stdout MUST be the complete frozen $reportName with summary fields, the mandatory table (one row per frozen ID), DATABASE EVIDENCE, LOG EVIDENCE, CODE EVIDENCE. Suggested fixes are candidates only. NO CHANGE is allowed." }
    $prompt=@"
You are $who on TRACK of the DAILY FAILED-TRADE STUDY. Unattended. Do not ask Jean anything.

HARD ISOLATION: do not read the other agent private/db folders, LIVE_TEAM_STATUS.md, LIVE_EVENTS.log, or the other agent's memory files.

READ: AGENTS.md, agent-docs/AGREED_KNOWLEDGE.md, agent-docs/DECISIONS.md, agent-docs/DATABASE_INVESTIGATION_RULES.md, $ownDocs, agent-docs/runs/$runId/CHARTER.md, your private files ($priorList), agent-docs/runs/$runId/db/$who/

ANALYSIS ONLY. No code/config/DB writes/git/deploy/implementation.

TIME: DB timestamps are UTC. Report KSA = UTC+3. Never label raw UTC as KSA.

FROZEN TRADES (mandatory, do not substitute):
$($tradeIds -join ', ')

$freeze

DATABASE: you do not have the password. Emit:
### DB_REQUEST
QueryId: DBQ-$who-I$iter-001
Purpose: ...
SQL:
SELECT ...
### END_DB_REQUEST

CURRENT TASK: $task
Iteration $iter of 8. Self-review only.
$iter8note
Label PROJECT EVIDENCE / EXTERNAL RESEARCH / AGENT INFERENCE.
"@
    $name=('iteration{0:D2}-{1}' -f $iter, $who.ToLowerInvariant())
    $out=Join-Path $priv "$name.txt"
    $err=Join-Path $priv "$name.stderr.txt"
    [IO.File]::WriteAllText((Join-Path $priv "$name.prompt.txt"),$prompt,$utf8)
    Write-LiveEvent $who 'STATUS' "Started $task"
    Update-LiveStatus -Next "$who is working on: $task"
    Say "START $who / $name"
    $timeoutMs=$ProcessTimeoutSeconds*1000
    if($who -eq 'CODEX'){
      $result=Invoke-NativeCapturedProcess -FileName $codex -ArgumentList @('--search','exec','--sandbox','read-only','-c','model_reasoning_effort=high','-C',$root,'-') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) Write-LiveEvent 'CODEX' 'STATUS' ('Still working - elapsed {0}m {1}s; task: {2}' -f [int]($sec/60),($sec % 60),$task); Update-LiveStatus -Next ('CODEX continues: '+$task) }
    } else {
      $result=Invoke-NativeCapturedProcess -FileName $claude -ArgumentList @('-p','--permission-mode','plan','--permission-prompts','none','--effort',$ClaudeEffort,'--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text') -WorkingDirectory $root -StdoutPath $out -StderrPath $err -StdinText $prompt -TimeoutMs $timeoutMs -Heartbeat { param($sec) Write-LiveEvent 'CLAUDE' 'STATUS' ('Still working - elapsed {0}m {1}s; task: {2}' -f [int]($sec/60),($sec % 60),$task); Update-LiveStatus -Next ('CLAUDE continues: '+$task) }
    }
    if($result.TimedOut){ throw "TIMEOUT $who / $name" }
    if($result.ExitCode -ne 0){ throw "$who failed ExitCode=$($result.ExitCode). See $err" }
    if([string]::IsNullOrWhiteSpace($result.Stdout)){ throw "$who empty output. See $err" }
    Say "DONE $who / $name"
    if($who -eq 'CODEX'){ $script:codexActivity="Completed $task" } else { $script:claudeActivity="Completed $task" }
    Write-LiveEvent $who 'STATUS' "Completed $task"
    Invoke-DbRequests $who $result.Stdout $iter
    if($iter -eq 8){
      [IO.File]::WriteAllText((Join-Path $priv $reportName),$result.Stdout,$utf8)
      Write-LiveEvent $who 'STATUS' "Frozen $reportName"
    }
  }
}

$codexFinal=[IO.File]::ReadAllText((Join-Path $codexPriv 'CODEX_FAILED_TRADE_STUDY.md'))
$claudeFinal=[IO.File]::ReadAllText((Join-Path $claudePriv 'CLAUDE_FAILED_TRADE_STUDY.md'))
$wtag='window2'
if($Window -eq 'WINDOW1'){ $wtag='window1' }
$mdPath=Join-Path $outDir ("failedtrade-$wtag-$dateStamp.md")
$pdfPath=Join-Path $outDir ("failedtrade-$wtag-$dateStamp.pdf")
$combined=@"
# DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY

Window: $Window
Run: $runId
Implementation authorization: NOT AUTHORIZED
Suggested fixes are candidates only and were not implemented.

$freeze

## SECTION 1 - CODEX ANALYSIS

$codexFinal

## SECTION 2 - CLAUDE ANALYSIS

$claudeFinal

## SECTION 3 - COORDINATOR COMMON FINDINGS

The Coordinator is a comparator, not a third trading analyst. It does not choose a winner.

Both agents were required to analyze the same frozen IDs: $($tradeIds -join ', ')

Read sections 1 and 2 for root causes, entry verdicts, proof IDs, and suggested fixes.
Agreement is FULL only when both primary classifications match; otherwise STRONG/PARTIAL/NONE as stated by the two frozen reports. The Coordinator does not invent a third classification.

CANDIDATE IMPROVEMENT - NOT APPROVED. No implementation.
"@
[IO.File]::WriteAllText($mdPath,$combined,$utf8)
$pdfOk=Publish-Pdf $mdPath $pdfPath
$script:livePhase='FROZEN DOCUMENT READY'
Update-LiveStatus -Next "Document ready: $mdPath"
Write-LiveEvent 'COORDINATOR' 'STATUS' "Document ready md=$mdPath pdfOk=$pdfOk"
Say "DOCUMENT_READY $mdPath"
}
finally {
  Exit-StudyLock
  if(-not $TestMode -and $Window -eq 'WINDOW1' -and (Test-Path -LiteralPath $queuePath)){
    Write-Host 'QUEUED_WINDOW2_STARTING_AFTER_WINDOW1'
    Remove-Item -LiteralPath $queuePath -Force -ErrorAction SilentlyContinue
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -Window WINDOW2 -ProjectRoot $root
  }
}
