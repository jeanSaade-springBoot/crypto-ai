param(
 [Parameter(Mandatory=$true)][string]$Specification,
 [Parameter(Mandatory=$true)][string]$ImplementationReport,
 [string]$ProjectRoot=(Get-Location).Path,
 [int]$TimeoutSeconds=3600
)
$ErrorActionPreference='Stop';$root=(Resolve-Path $ProjectRoot).Path;$spec=(Resolve-Path $Specification).Path;$impl=(Resolve-Path $ImplementationReport).Path
function LatestBinary([string]$pattern,[string]$relative){$base=Join-Path $env:USERPROFILE '.cursor\extensions';$dirs=Get-ChildItem $base -Directory|?{$_.Name -like $pattern}|sort LastWriteTime -Descending;foreach($d in $dirs){$p=Join-Path $d.FullName $relative;if(Test-Path $p){return $p}};throw "Binary missing: $pattern"}
function Q([string]$s){if($s -notmatch '[\s"]'){return $s};'"'+($s -replace '(\\*)"','$1$1\"' -replace '(\\+)$','$1$1')+'"'}
function Run([string]$exe,[string[]]$args,[string]$stdin){$psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=$exe;$psi.WorkingDirectory=$root;$psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true;$psi.Arguments=($args|%{Q $_}) -join ' ';$p=New-Object Diagnostics.Process;$p.StartInfo=$psi;[void]$p.Start();$p.StandardInput.Write($stdin);$p.StandardInput.Close();$ot=$p.StandardOutput.ReadToEndAsync();$et=$p.StandardError.ReadToEndAsync();if(-not$p.WaitForExit($TimeoutSeconds*1000)){try{$p.Kill()}catch{};throw 'TIMEOUT'};$o=$ot.Result;$e=$et.Result;if($p.ExitCode -ne 0){throw "Exit $($p.ExitCode): $e"};$o}
$codex=LatestBinary 'openai.chatgpt-*' 'bin\windows-x86_64\codex.exe';$claude=LatestBinary 'anthropic.claude-code-*' 'resources\native-binary\claude.exe';$s=[IO.File]::ReadAllText($spec);$i=[IO.File]::ReadAllText($impl)
$prompt=@"
Validate the COMPLETE current implementation against the exact approved specification and implementation report below. Read AGENTS.md. Do not edit. Verify registry reconciliation, tests, required logging, DB/config impact, Replay/Production parity, regressions, UTC/KSA rules, and unregistered changes. Distinguish PROVEN_BY_TEST, SUPPORTED_BY_CODE_INSPECTION, ASSUMED, NOT_TESTED. Return one exact verdict line: VERDICT: APPROVED or VERDICT: REJECTED, then FINDINGS/EVIDENCE/UNKNOWNS.
SPECIFICATION:
$s
IMPLEMENTATION REPORT:
$i
"@
$c=Run $codex @('--search','exec','--sandbox','read-only','-c','model_reasoning_effort=high','-C',$root,'-') $prompt;$l=Run $claude @('-p','--permission-mode','plan','--permission-prompts','none','--effort','max','--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text') $prompt
$cv=if($c-match'(?im)^\s*VERDICT\s*:\s*APPROVED\s*$'){'APPROVED'}else{'REJECTED'};$lv=if($l-match'(?im)^\s*VERDICT\s*:\s*APPROVED\s*$'){'APPROVED'}else{'REJECTED'}
$run=Join-Path $root ('agent-docs\runs\validation-'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory -Force $run|Out-Null;$report=Join-Path $run 'VALIDATION_REPORT.md'
$body="# VALIDATION REPORT`n`nCodex: $cv`nClaude: $lv`n`n## Codex validation`n$c`n`n## Claude validation`n$l`n`nImplementation acceptance: $(if($cv -eq 'APPROVED' -and $lv -eq 'APPROVED'){'DUAL-AGENT VALIDATED'}else{'NOT VALIDATED'})`n"
[IO.File]::WriteAllText($report,$body)
$freeze=Join-Path $root 'scripts\freeze-agent-artifact.ps1';& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $freeze -Source $report -Revision 'R01' -Codex $cv -Claude $lv -Authorization 'VALIDATION ONLY - NO DEPLOYMENT AUTHORIZATION'
Write-Host "VALIDATION_REPORT=$report";if($cv -ne 'APPROVED' -or $lv -ne 'APPROVED'){exit 3}
