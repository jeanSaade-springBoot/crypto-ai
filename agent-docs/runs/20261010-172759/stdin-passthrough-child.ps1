param([string]$OutputPath)
$ErrorActionPreference='Stop'
$ms=New-Object System.IO.MemoryStream
[Console]::OpenStandardInput().CopyTo($ms)
[IO.File]::WriteAllBytes($OutputPath, $ms.ToArray())