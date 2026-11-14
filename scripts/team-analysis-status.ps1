# Read-only snapshot of persisted coordinator evidence. Compatible with Windows PowerShell 5.1.
[CmdletBinding()]
param(
  [ValidateSet('Quick','Full','History')][string]$Mode='Quick',
  [string]$ProjectRoot=(Get-Location).Path,
  [string]$RunId=''
)
$ErrorActionPreference='Stop'
$docs=Join-Path $ProjectRoot 'agent-docs'
$runs=Join-Path $docs 'runs'
if(-not (Test-Path -LiteralPath $runs)) { Write-Output 'NO RUNS FOUND'; exit 0 }
if(-not $RunId) {
  $selected=Get-ChildItem -LiteralPath $runs -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'PROGRESS.log') } | Sort-Object Name -Descending | Select-Object -First 1
} else {
  $candidate=Join-Path $runs $RunId
  if(-not (Test-Path -LiteralPath $candidate -PathType Container)) { throw 'Requested run not found.' }
  $selected=Get-Item -LiteralPath $candidate
}
if(-not $selected) { Write-Output 'NO COORDINATOR RUN FOUND'; exit 0 }
$run=$selected.FullName
$progress=Join-Path $run 'PROGRESS.log'
$events=Join-Path $run 'LIVE_EVENTS.log'
Write-Output ('RUN: '+$selected.Name)
Write-Output ('SNAPSHOT: '+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Output 'READ ONLY: does not launch agents or alter coordinator state.'
if(Test-Path -LiteralPath $progress) {
  Write-Output '--- LATEST PROGRESS ---'
  Get-Content -LiteralPath $progress -Tail 12
}
if($Mode -eq 'Quick') {
  if(Test-Path -LiteralPath $events) { Write-Output '--- LATEST EVENTS ---'; Get-Content -LiteralPath $events -Tail 12 }
  Write-Output 'Detailed: team-analysis-status.ps1 -Mode Full'
  exit 0
}
$roundFiles=@()
$roundFiles += Get-ChildItem -LiteralPath $run -File -Filter 'round*-*.txt' -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^round\d+-(codex|claude)\.txt$' }
foreach($privAgent in @('CODEX','CLAUDE')) {
  $priv=Join-Path $run ('private\'+$privAgent)
  if(Test-Path -LiteralPath $priv) {
    $roundFiles += Get-ChildItem -LiteralPath $priv -File -Filter 'iteration*-*.txt' -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^iteration\d+-(codex|claude)\.txt$' }
  }
}
$roundFiles=$roundFiles | Sort-Object Name
if($Mode -eq 'History') {
  Write-Output '--- COMPLETED AGENT ROUNDS (recorded artifacts only) ---'
  foreach($f in $roundFiles) {
    Write-Output ($f.Name+' | '+$f.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')+' | '+$f.Length+' bytes')
    $lines=Get-Content -LiteralPath $f.FullName -ErrorAction SilentlyContinue | Where-Object { $_ -match '(?i)^(VERDICT|FINDING|CHALLENGE|DISAGREEMENT|RESOLVED|POSITION|UNKNOWN|EVIDENCE)\s*:' } | Select-Object -First 8
    foreach($line in $lines) { Write-Output ('  '+$line) }
  }
  if(-not $roundFiles) { Write-Output 'No completed agent-round files yet.' }
  exit 0
}
Write-Output '--- CODEX FINDINGS (LATEST COMPLETED TURN) ---'
$codexFile=$roundFiles | Where-Object { $_.Name -match '-codex\.txt$' } | Select-Object -Last 1
if($codexFile) { Write-Output ('Source: '+$codexFile.Name); Get-Content -LiteralPath $codexFile.FullName -Tail 65 } else { Write-Output 'No completed Codex result.' }
Write-Output 'NOTE: Independent-study tracks must not be treated as consensus.'
Write-Output '--- CLAUDE FINDINGS (LATEST COMPLETED TURN) ---'
$claudeFile=$roundFiles | Where-Object { $_.Name -match '-claude\.txt$' } | Select-Object -Last 1
if($claudeFile) { Write-Output ('Source: '+$claudeFile.Name); Get-Content -LiteralPath $claudeFile.FullName -Tail 65 } else { Write-Output 'No completed Claude result.' }
$findings=Join-Path $run 'TEAM_FINDINGS.md'
if(Test-Path -LiteralPath $findings) { Write-Output '--- TEAM FINDINGS / AGREEMENTS / DISAGREEMENTS ---'; Get-Content -LiteralPath $findings -Tail 100 }
Write-Output '--- DATABASE: EXACT TABLES FOUND IN EXECUTED SQL ARTIFACTS ---'
$dbdir=Join-Path $run 'db'
$dbDirs=@()
if(Test-Path -LiteralPath $dbdir) { $dbDirs += $dbdir }
foreach($sub in @('CODEX','CLAUDE')) {
  $p=Join-Path $dbdir $sub
  if(Test-Path -LiteralPath $p) { $dbDirs += $p }
}
if($dbDirs.Count) {
  $sqlFiles=@()
  foreach($d in $dbDirs) { $sqlFiles += Get-ChildItem -LiteralPath $d -File -Filter '*.sql' -ErrorAction SilentlyContinue }
} else { $sqlFiles=@() }
if($dbDirs.Count) {
  $tableMap=@{}
  foreach($f in $sqlFiles) {
    $sql=[IO.File]::ReadAllText($f.FullName)
    $matches=[regex]::Matches($sql,'(?i)\b(?:FROM|JOIN|UPDATE|INTO|TABLE|DESCRIBE|DESC)\s+`?([a-zA-Z_][\w]*)(?:`?\s*\.\s*`?([a-zA-Z_][\w]*))?`?')
    foreach($m in $matches) {
      $table=if($m.Groups[2].Success){$m.Groups[1].Value+'.'+$m.Groups[2].Value}else{$m.Groups[1].Value}
      if(-not $tableMap.ContainsKey($table)) { $tableMap[$table]=New-Object System.Collections.Generic.List[string] }
      $tableMap[$table].Add($f.Name)
    }
  }
  if($tableMap.Count) { foreach($key in ($tableMap.Keys | Sort-Object)) { Write-Output ($key+' | SQL files: '+(($tableMap[$key] | Select-Object -Unique) -join ', ')) } }
  else { Write-Output 'No table names extractable from SQL files. Check SHOW/metadata queries manually.' }
  Write-Output '--- DATABASE QUERY EVIDENCE FILES ---'
  $sqlFiles | Select-Object -ExpandProperty Name
  $audit=Join-Path $dbdir 'DATABASE_QUERY_LOG.md'
  if(Test-Path -LiteralPath $audit) { Get-Content -LiteralPath $audit -Tail 45 }
  Write-Output 'NOTE: Table names parsed from SQL files are candidates; inspect query results to confirm execution and success.'
} else { Write-Output 'No DB evidence directory for this run.' }
Write-Output '--- LOGS: RECORDED LOG EVIDENCE ---'
$logArtifacts=Get-ChildItem -LiteralPath $run -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '(?i)(log[-_]?evidence|log[-_]?search|LOG-\d+)' } | Select-Object -First 35
if($logArtifacts) { $logArtifacts | ForEach-Object { Write-Output $_.FullName } } else { Write-Output 'No dedicated log evidence artifacts found; check raw agent transcripts.' }
Write-Output '--- LATEST LIVE EVENTS ---'
if(Test-Path -LiteralPath $events) { Get-Content -LiteralPath $events -Tail 30 } else { Write-Output 'No live events recorded.' }
Write-Output 'Evidence gaps must remain UNKNOWN; this command does not invent findings.'
