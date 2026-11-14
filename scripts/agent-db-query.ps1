param(
 [Parameter(Mandatory=$true)][string]$Query,
 [ValidateSet('CODEX','CLAUDE','COORDINATOR')][string]$Agent='COORDINATOR',
 [string]$RunId='manual',
 [string]$QueryId,
 [string]$Purpose='read-only investigation',
 [string]$ProjectRoot=(Get-Location).Path
)
$ErrorActionPreference='Stop'
$hostName=if($env:AGENT_DB_HOST){$env:AGENT_DB_HOST}else{'localhost'};$port=if($env:AGENT_DB_PORT){$env:AGENT_DB_PORT}else{'3307'};$db=if($env:AGENT_DB_NAME){$env:AGENT_DB_NAME}else{'crypto_ai'};$user=if($env:AGENT_DB_USER){$env:AGENT_DB_USER}else{'ai_investigator'};$pwd=$env:AGENT_DB_PASSWORD;$mysql=if($env:AGENT_MYSQL_EXE){$env:AGENT_MYSQL_EXE}else{'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'}
if($db -ne 'crypto_ai'){throw 'BLOCKED: agent DB wrapper is restricted to crypto_ai.'};if([string]::IsNullOrWhiteSpace($pwd)){throw 'AGENT_DB_PASSWORD is not set.'};if(-not(Test-Path $mysql)){throw "mysql.exe not found: $mysql"}
$forbidden='(?is)\b(INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|CALL|SET|LOAD|IMPORT|LOCK|UNLOCK|START\s+TRANSACTION|COMMIT|ROLLBACK|INTO\s+OUTFILE|INTO\s+DUMPFILE)\b'
if($Query -match $forbidden){throw 'BLOCKED BY WRAPPER before MySQL execution: non-read-only SQL detected.'}
$parts=$Query -split ';' | %{$_.Trim()} | ?{$_};foreach($s in $parts){if($s -notmatch '^(?is)\s*(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN)\b'){throw "BLOCKED BY WRAPPER before MySQL execution: statement not allowed: $s"}}
if(-not $QueryId){$QueryId='DBQ-'+(Get-Date -Format 'yyyyMMddHHmmssfff')}
$root=(Resolve-Path $ProjectRoot).Path;$dir=Join-Path $root "agent-docs\runs\$RunId\db";New-Item -ItemType Directory -Force $dir|Out-Null
$sqlPath=Join-Path $dir "$QueryId-$Agent.sql";$resPath=Join-Path $dir "$QueryId-$Agent-result.txt";$audit=Join-Path $dir 'DATABASE_QUERY_LOG.md';[IO.File]::WriteAllText($sqlPath,$Query,[Text.UTF8Encoding]::new($false))
$old=$env:MYSQL_PWD;$env:MYSQL_PWD=$pwd;$sw=[Diagnostics.Stopwatch]::StartNew()
try{$out=& $mysql '-h' $hostName '-P' $port '-u' $user '-D' $db '--batch' '--raw' '-e' $Query 2>&1;$exit=$LASTEXITCODE}finally{$sw.Stop();$env:MYSQL_PWD=$old}
$text=($out|Out-String).Trim();[IO.File]::WriteAllText($resPath,$text,[Text.UTF8Encoding]::new($false));$rows=if($text){([regex]::Matches($text,"`n").Count)}else{0};Add-Content $audit "`n## $QueryId`n- timestamp: $(Get-Date -Format o)`n- agent: $Agent`n- purpose: $Purpose`n- SQL file: $([IO.Path]::GetFileName($sqlPath))`n- exit code: $exit`n- duration_ms: $($sw.ElapsedMilliseconds)`n- approximate output lines: $rows`n- password: never logged`n"
if($exit -ne 0){throw "MySQL read failed ExitCode=$exit. See $resPath"};Write-Output $text
