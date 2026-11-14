param(
  [Parameter(Mandatory=$true)][ValidateSet('CLAUDE','CODEX')][string]$Peer,
  [Parameter(Mandatory=$true)][string]$Question,
  [string]$ProjectRoot=(Get-Location).Path,
  [string]$Artifact,
  [switch]$Deep,
  [int]$TimeoutSeconds=1800
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path $ProjectRoot).Path
function LatestBinary([string]$pattern,[string]$relative) {
  $base=Join-Path $env:USERPROFILE '.cursor\extensions'
  $dirs=Get-ChildItem $base -Directory -ErrorAction Stop | Where-Object {$_.Name -like $pattern} | Sort-Object LastWriteTime -Descending
  foreach($d in $dirs){$p=Join-Path $d.FullName $relative;if(Test-Path $p){return $p}}
  throw "No executable found for $pattern / $relative"
}
function Quote-Arg([string]$s){ if($null -eq $s){return '""'}; if($s -notmatch '[\s"]'){return $s}; return '"'+($s -replace '(\\*)"','$1$1\"' -replace '(\\+)$','$1$1')+'"' }
function Invoke-Captured([string]$exe,[string[]]$args,[string]$stdin){
  $psi=New-Object Diagnostics.ProcessStartInfo
  $psi.FileName=$exe;$psi.WorkingDirectory=$root;$psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true
  $psi.Arguments=($args|ForEach-Object{Quote-Arg $_}) -join ' '
  $p=New-Object Diagnostics.Process;$p.StartInfo=$psi
  [void]$p.Start();$p.StandardInput.Write($stdin);$p.StandardInput.Close()
  $outTask=$p.StandardOutput.ReadToEndAsync();$errTask=$p.StandardError.ReadToEndAsync()
  if(-not $p.WaitForExit($TimeoutSeconds*1000)){try{$p.Kill()}catch{};throw "Peer consultation TIMEOUT after ${TimeoutSeconds}s"}
  $out=$outTask.Result;$err=$errTask.Result
  if($p.ExitCode -ne 0){throw "Peer consultation failed ExitCode=$($p.ExitCode): $err"}
  return $out
}
$context=''
if($Artifact){if(-not(Test-Path $Artifact)){throw "Artifact not found: $Artifact"};$context=[IO.File]::ReadAllText((Resolve-Path $Artifact).Path)}
$prompt=@"
You are an independent peer reviewer for crypto-ai-trader. Read AGENTS.md and the relevant project docs first. This is ONE peer consultation, NOT formal team consensus and NOT implementation authorization.
Question: $Question
$(if($context){"Artifact/context to review:`n$context"}else{'Inspect the repository only as needed for the question.'})
Use public web research when useful. Distinguish PROJECT EVIDENCE, EXTERNAL RESEARCH, and AGENT INFERENCE. Never expose secrets. Do not modify application code or database. Return a concise review with: VERDICT, FINDINGS, EVIDENCE, OBJECTIONS, UNKNOWNS, NEXT RECOMMENDATION.
"@
if($Peer -eq 'CLAUDE'){
  $exe=LatestBinary 'anthropic.claude-code-*' 'resources\native-binary\claude.exe'
  $effort=if($Deep){'max'}else{'high'}
  $args=@('-p','--permission-mode','plan','--permission-prompts','none','--effort',$effort,'--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text')
}else{
  $exe=LatestBinary 'openai.chatgpt-*' 'bin\windows-x86_64\codex.exe'
  $effort=if($Deep){'high'}else{'medium'}
  $args=@('--search','exec','--sandbox','read-only','-c',"model_reasoning_effort=$effort",'-C',$root,'-')
}
Write-Host "===== $Peer PEER REVIEW ====="
$result=Invoke-Captured $exe $args $prompt
Write-Output $result
Write-Host "===== END $Peer PEER REVIEW ====="
