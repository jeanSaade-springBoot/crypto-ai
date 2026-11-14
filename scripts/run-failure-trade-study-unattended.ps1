param(
  [Parameter(Mandatory=$true)][ValidateSet('WINDOW1','WINDOW2')][string]$Window,
  [string]$ProjectRoot='C:\workspace\crypto-ai-trader-mysql'
)
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($env:AGENT_DB_PASSWORD)){
  $env:AGENT_DB_PASSWORD=[Environment]::GetEnvironmentVariable('AGENT_DB_PASSWORD','User')
}
if([string]::IsNullOrWhiteSpace($env:AGENT_DB_PASSWORD)){
  throw 'AGENT_DB_PASSWORD is not set in the user environment. Scheduled study cannot open crypto_ai.'
}
$launcher=Join-Path $ProjectRoot 'scripts\run-failure-trade-study.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -Window $Window -ProjectRoot $ProjectRoot
exit $LASTEXITCODE
