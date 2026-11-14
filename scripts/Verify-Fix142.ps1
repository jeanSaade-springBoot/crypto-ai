# FIX-142: build/test only. This script NEVER deploys or connects to production.
[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21',
    [switch]$RunMySql,
    [string]$TestMySqlUrl,
    [string]$ExpectedTestServerUuid
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) { throw "JDK not found: $JavaHome" }
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $version = (& mvn -version 2>&1 | Out-String)
    Write-Host $version
    if ($version -notmatch 'Java version: 21\.') { throw 'Maven must use Java 21 before compiling' }
    & mvn -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Full build/test suite failed; do not deploy' }
    if ($RunMySql) {
        if ($TestMySqlUrl -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost):[0-9]+/fix142_test_[A-Za-z0-9_]+(\?.*)?$') {
            throw 'Use a separately provisioned loopback MySQL test instance and fix142_test_ bootstrap schema'
        }
        if ([string]::IsNullOrWhiteSpace($ExpectedTestServerUuid)) { throw 'Reviewed dedicated test-server UUID is required' }
        $credential = Get-Credential -Message 'Dedicated disposable test MySQL credentials'
        $env:FIX142_TEST_MYSQL_URL = $TestMySqlUrl
        $env:FIX142_TEST_MYSQL_SERVER_UUID = $ExpectedTestServerUuid
        $env:FIX142_TEST_MYSQL_DISPOSABLE = 'YES'
        $env:FIX142_TEST_MYSQL_USER = $credential.UserName
        $env:FIX142_TEST_MYSQL_PASSWORD = $credential.GetNetworkCredential().Password
        try {
            & mvn -B -ntp -Pfix142-mysql verify
            if ($LASTEXITCODE -ne 0) { throw 'MySQL tests failed; do not deploy' }
        } finally {
            Remove-Item Env:FIX142_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
            $credential = $null
        }
    } else { Write-Warning 'MySQL acceptance was not run. A green unit build is not release acceptance.' }
} finally { Pop-Location }
