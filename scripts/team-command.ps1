param(
 [Parameter(Mandatory=$true)][string]$Command,
 [string]$Argument,
 [string]$ProjectRoot=(Get-Location).Path,
 [string]$Artifact,
 [ValidateSet('CODEX','CLAUDE')][string]$From='CODEX'
)
$ErrorActionPreference='Stop';$root=(Resolve-Path $ProjectRoot).Path;$c=$Command.Trim().ToLowerInvariant()
switch($c){
 'team commands' {Get-Content (Join-Path $root 'agent-docs\COMMANDS.md');break}
 'proceed claude' {& (Join-Path $root 'scripts\team-peer.ps1') -Peer CLAUDE -Question $Argument -ProjectRoot $root -Artifact $Artifact;break}
 'proceed claude deep' {& (Join-Path $root 'scripts\team-peer.ps1') -Peer CLAUDE -Question $Argument -ProjectRoot $root -Artifact $Artifact -Deep;break}
 'proceed codex' {& (Join-Path $root 'scripts\team-peer.ps1') -Peer CODEX -Question $Argument -ProjectRoot $root -Artifact $Artifact;break}
 'proceed codex deep' {& (Join-Path $root 'scripts\team-peer.ps1') -Peer CODEX -Question $Argument -ProjectRoot $root -Artifact $Artifact -Deep;break}
 'proceed coordinator' {& (Join-Path $root 'scripts\team-handoff.ps1') -From $From -Question $Argument -Type TECHNICAL_DESIGN -Artifact $Artifact -ProjectRoot $root;break}
 'team status' {Get-Content (Join-Path $root 'agent-docs\LIVE_TEAM_STATUS.md');break}
 'team live' {Get-Content (Join-Path $root 'agent-docs\LIVE_TEAM_STATUS.md');break}
 default {throw "Unknown executable team command: $Command. Use 'team commands'."}
}
