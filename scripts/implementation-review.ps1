param(
 [Parameter(Mandatory=$true)][string]$Specification,
 [Parameter(Mandatory=$true)][string]$ProjectRoot,
 [switch]$UserAuthorized,
 [int]$MaxReviewRounds=0,
 [int]$ProcessTimeoutSeconds=3600
)
$ErrorActionPreference='Stop'
if(-not $UserAuthorized){throw 'BLOCKED: explicit -UserAuthorized is required. This script must never infer user approval.'}
$root=(Resolve-Path $ProjectRoot).Path;$spec=(Resolve-Path $Specification).Path
function LatestBinary([string]$pattern,[string]$relative){$base=Join-Path $env:USERPROFILE '.cursor\extensions';$dirs=Get-ChildItem $base -Directory|?{$_.Name -like $pattern}|sort LastWriteTime -Descending;foreach($d in $dirs){$p=Join-Path $d.FullName $relative;if(Test-Path $p){return $p}};throw "Binary missing: $pattern"}
function Q([string]$s){if($s -notmatch '[\s"]'){return $s};'"'+($s -replace '(\\*)"','$1$1\"' -replace '(\\+)$','$1$1')+'"'}
function Run([string]$exe,[string[]]$args,[string]$stdin){$psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=$exe;$psi.WorkingDirectory=$root;$psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true;$psi.Arguments=($args|%{Q $_}) -join ' ';$p=New-Object Diagnostics.Process;$p.StartInfo=$psi;[void]$p.Start();$p.StandardInput.Write($stdin);$p.StandardInput.Close();$ot=$p.StandardOutput.ReadToEndAsync();$et=$p.StandardError.ReadToEndAsync();if(-not$p.WaitForExit($ProcessTimeoutSeconds*1000)){try{$p.Kill()}catch{};throw 'TIMEOUT'};$o=$ot.Result;$e=$et.Result;if($p.ExitCode -ne 0){throw "Exit $($p.ExitCode): $e"};$o}
$codex=LatestBinary 'openai.chatgpt-*' 'bin\windows-x86_64\codex.exe';$claude=LatestBinary 'anthropic.claude-code-*' 'resources\native-binary\claude.exe'
$specText=[IO.File]::ReadAllText($spec);$runId=Get-Date -Format 'yyyyMMdd-HHmmss';$run=Join-Path $root "agent-docs\runs\implementation-$runId";New-Item -ItemType Directory -Force $run|Out-Null
$implPrompt=@"
USER AUTHORIZATION HAS BEEN EXPLICITLY SUPPLIED TO THE IMPLEMENTATION SCRIPT.
Read AGENTS.md and the frozen specification below. You are CODEX IMPLEMENTER. Implement ONLY registered approved scope. No unrelated refactoring. Preserve UTC/KSA rules. DB writes are not authorized unless the specification explicitly registers a migration and the user separately authorized it; do not deploy. Add required logs/tests/comments. If a material design deviation is required, STOP and report DESIGN_DEVIATION_REQUIRED instead of improvising.
SPECIFICATION:
$specText
"@
$impl=Run $codex @('--search','exec','--sandbox','workspace-write','-a','never','-c','model_reasoning_effort=high','-C',$root,'-') $implPrompt;[IO.File]::WriteAllText((Join-Path $run 'codex-implementation.txt'),$impl)
$round=0
while($true){$round++;if($MaxReviewRounds -gt 0 -and $round -gt $MaxReviewRounds){throw 'Review ceiling reached without approval'}
 $reviewPrompt=@"
You are CLAUDE, mandatory independent reviewer. Read AGENTS.md, the exact approved specification, and inspect the COMPLETE current implementation/diff. Do not edit. Review whole affected flow, callers, concurrency, DB/config, logging, Replay/Production parity, tests, regressions, and registry conformance. Any material issue means REJECTED. Return exactly one verdict line `VERDICT: APPROVED` or `VERDICT: REJECTED`, followed by FINDINGS/EVIDENCE/OBJECTIONS/UNKNOWNS.
SPECIFICATION:
$specText
"@
 $review=Run $claude @('-p','--permission-mode','plan','--permission-prompts','none','--effort','max','--tools','Read,Glob,Grep,WebSearch,WebFetch','--disallowedTools','Bash,Edit,Write','--output-format','text') $reviewPrompt;[IO.File]::WriteAllText((Join-Path $run ("claude-review-{0:D2}.txt" -f $round)),$review)
 if($review -match '(?im)^\s*VERDICT\s*:\s*APPROVED\s*$'){break}
 $fixPrompt=@"
You are CODEX IMPLEMENTER. Claude rejected the implementation below. Evaluate EVERY finding as ACCEPTED or DISPUTED WITH EVIDENCE. Fix accepted findings within the approved specification. Do not broaden scope. If a finding requires material design deviation, STOP and report DESIGN_DEVIATION_REQUIRED. Re-run appropriate tests. CLAUDE REVIEW:
$review
"@
 $fix=Run $codex @('--search','exec','--sandbox','workspace-write','-a','never','-c','model_reasoning_effort=high','-C',$root,'-') $fixPrompt;[IO.File]::WriteAllText((Join-Path $run ("codex-correction-{0:D2}.txt" -f $round)),$fix)
}
$verifyPrompt=@"
Claude has APPROVED the complete implementation. Perform final read-only verification of the exact current state against the specification. Confirm no implementation changes occurred after Claude approval, tests/status, registry scope, required logs/docs, and report any remaining unknowns. Do not edit. Return VERDICT: APPROVED only if the exact state is ready for post-implementation validation.
SPECIFICATION:
$specText
"@
$verify=Run $codex @('--search','exec','--sandbox','read-only','-c','model_reasoning_effort=high','-C',$root,'-') $verifyPrompt
if($verify -notmatch '(?im)^\s*VERDICT\s*:\s*APPROVED\s*$'){throw 'Codex final verification did not approve the exact Claude-approved state.'}
$report=@"
# IMPLEMENTATION REPORT
Run: implementation-$runId
Specification: $spec
Claude full-review rounds: $round
Claude final verdict: APPROVED
Codex final verification: APPROVED
User authorization was explicitly supplied to the implementation script.
No deployment authorization is implied.

## Codex implementation summary
$impl

## Codex final verification
$verify
"@
$reportPath=Join-Path $run 'IMPLEMENTATION_REPORT.md';[IO.File]::WriteAllText($reportPath,$report)
$freeze=Join-Path $root 'scripts\freeze-agent-artifact.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $freeze -Source $reportPath -Revision 'R01' -Codex APPROVED -Claude APPROVED -Authorization 'IMPLEMENTED / NOT DEPLOYMENT AUTHORIZED'
if($LASTEXITCODE -ne 0){throw 'Implementation report PDF freeze failed'}
Write-Host "IMPLEMENTATION REVIEW COMPLETE: $reportPath"
