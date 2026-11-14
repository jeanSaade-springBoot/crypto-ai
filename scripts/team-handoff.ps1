param(
 [Parameter(Mandatory=$true)][ValidateSet('CODEX','CLAUDE')][string]$From,
 [Parameter(Mandatory=$true)][string]$Question,
 [ValidateSet('GENERAL','INVESTIGATION','TECHNICAL_DESIGN','CODE_REVIEW')][string]$Type='GENERAL',
 [string]$Artifact,
 [string]$Summary,
 [string]$ProjectRoot=(Get-Location).Path,
 [int]$MaxRounds=12
)
$ErrorActionPreference='Stop'
$coord=Join-Path (Resolve-Path $ProjectRoot).Path 'agent-docs\coordinator.ps1'
if(-not(Test-Path $coord)){throw "Coordinator not found: $coord"}
$args=@('-NoProfile','-ExecutionPolicy','Bypass','-File',$coord,'-ProjectRoot',(Resolve-Path $ProjectRoot).Path,'-Question',$Question,'-HandoffFrom',$From,'-HandoffType',$Type,'-MaxRounds',$MaxRounds)
if($Artifact){$args+=@('-HandoffFile',(Resolve-Path $Artifact).Path)}
if($Summary){$args+=@('-HandoffSummary',$Summary)}
& powershell.exe @args
exit $LASTEXITCODE
