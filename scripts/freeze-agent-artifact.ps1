param(
 [Parameter(Mandatory=$true)][string]$Source,
 [Parameter(Mandatory=$true)][string]$Revision,
 [ValidateSet('APPROVED','REJECTED','UNKNOWN')][string]$Codex='UNKNOWN',
 [ValidateSet('APPROVED','REJECTED','UNKNOWN')][string]$Claude='UNKNOWN',
 [string]$Authorization='NOT AUTHORIZED',
 [string]$Output
)
$ErrorActionPreference='Stop'
$src=(Resolve-Path $Source).Path
if(-not $Output){$dir=Split-Path $src;$base=[IO.Path]::GetFileNameWithoutExtension($src);$Output=Join-Path $dir "$base-$Revision.pdf"}
$py=(Get-Command python -ErrorAction SilentlyContinue);if(-not $py){throw 'Python is required for PDF generation.'}
$script=Join-Path $PSScriptRoot 'generate-agent-pdf.py'
& $py.Source $script --source $src --output $Output --revision $Revision --codex $Codex --claude $Claude --status $Authorization
if($LASTEXITCODE -ne 0){throw 'PDF generation failed'}
Write-Host "Frozen PDF: $Output"
