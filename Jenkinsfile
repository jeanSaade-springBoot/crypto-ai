pipeline {
    agent any

    tools {
        maven 'Maven'
    }

    environment {
        DEPLOY_DIR = 'C:\\apps\\crypto-ai'
        APP_PORT = '8080'
        APP_JAR = 'crypto-ai.jar'
        APP_LOG = 'crypto-ai.log'
        BACKUP_JAR = 'crypto-ai-backup.jar'
        HEALTH_URL = 'http://localhost:8080/login'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                bat '''
                @echo off
                echo Building Crypto AI...
                call mvn clean package -DskipTests

                if errorlevel 1 (
                    echo Maven build failed.
                    exit /b 1
                )

                echo Maven build completed successfully.
                '''
            }
        }

        stage('Stop old application') {
            steps {
                bat '''
                @echo off
                setlocal EnableDelayedExpansion

                echo Checking for an existing Crypto AI process on port %APP_PORT%...
                set "FOUND_PROCESS=false"

                for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%APP_PORT%" ^| findstr "LISTENING"') do (
                    set "FOUND_PROCESS=true"
                    echo Stopping process PID %%a...
                    taskkill /PID %%a /F >nul 2>&1
                )

                if "!FOUND_PROCESS!"=="false" (
                    echo No existing process was listening on port %APP_PORT%.
                )

                endlocal
                exit /b 0
                '''
            }
        }

        stage('Wait for port release') {
            steps {
                powershell '''
                    Write-Host "Waiting for port $env:APP_PORT to be released..."

                    $maxAttempts = 12
                    $released = $false

                    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                        $listener = Get-NetTCPConnection `
                            -LocalPort ([int]$env:APP_PORT) `
                            -State Listen `
                            -ErrorAction SilentlyContinue

                        if (-not $listener) {
                            Write-Host "Port $env:APP_PORT is available."
                            $released = $true
                            break
                        }

                        Write-Host "Port is still in use. Attempt $attempt of $maxAttempts..."
                        Start-Sleep -Seconds 2
                    }

                    if (-not $released) {
                        Write-Error "Port $env:APP_PORT could not be released."
                        exit 1
                    }
                '''
            }
        }

        stage('Prepare deployment folder') {
            steps {
                bat '''
                @echo off

                if not exist "%DEPLOY_DIR%" (
                    echo Creating deployment directory...
                    mkdir "%DEPLOY_DIR%"
                )

                if errorlevel 1 (
                    echo Failed to create deployment directory.
                    exit /b 1
                )

                if exist "%DEPLOY_DIR%\\%APP_JAR%" (
                    echo Creating backup of the current JAR...
                    copy /Y "%DEPLOY_DIR%\\%APP_JAR%" "%DEPLOY_DIR%\\%BACKUP_JAR%" >nul

                    if errorlevel 1 (
                        echo Failed to create backup.
                        exit /b 1
                    )

                    echo Backup created successfully.
                ) else (
                    echo No existing JAR was found. Backup skipped.
                )
                '''
            }
        }

        stage('Deploy new JAR') {
            steps {
                bat '''
                @echo off
                setlocal EnableDelayedExpansion

                set "FOUND_JAR="

                for %%f in (target\\*.jar) do (
                    set "FOUND_JAR=%%f"
                )

                if not defined FOUND_JAR (
                    echo No executable JAR was found in the target folder.
                    exit /b 1
                )

                echo Deploying !FOUND_JAR!...
                copy /Y "!FOUND_JAR!" "%DEPLOY_DIR%\\%APP_JAR%" >nul

                if errorlevel 1 (
                    echo Failed to copy the application JAR.
                    exit /b 1
                )

                echo New JAR deployed successfully.
                endlocal
                '''
            }
        }

        stage('Start application') {
            steps {
                bat '''
                @echo off

                cd /d "%DEPLOY_DIR%"

                if exist "%APP_LOG%" (
                    del /Q "%APP_LOG%"
                )

                echo Starting Crypto AI on port %APP_PORT%...
                set JENKINS_NODE_COOKIE=crypto-ai-dont-kill

                start "Crypto AI" /B javaw ^
                    -jar "%APP_JAR%" ^
                    --server.port=%APP_PORT% ^
                    > "%APP_LOG%" 2>&1

                echo Startup command executed.
                '''
            }
        }

        stage('Verify port') {
            steps {
                powershell '''
                    Write-Host "Checking port $env:APP_PORT..."

                    $maxAttempts = 18
                    $delaySeconds = 5
                    $listening = $false

                    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                        $listener = Get-NetTCPConnection `
                            -LocalPort ([int]$env:APP_PORT) `
                            -State Listen `
                            -ErrorAction SilentlyContinue

                        if ($listener) {
                            Write-Host "Crypto AI is listening on port $env:APP_PORT."
                            Write-Host "PID: $($listener.OwningProcess)"
                            $listening = $true
                            break
                        }

                        Write-Host "Port check attempt $attempt of $maxAttempts failed."
                        Start-Sleep -Seconds $delaySeconds
                    }

                    if (-not $listening) {
                        Write-Host ""
                        Write-Host "Crypto AI did not start successfully."
                        Write-Host "Application log:"
                        Write-Host "----------------------------------------"

                        $logPath = Join-Path $env:DEPLOY_DIR $env:APP_LOG

                        if (Test-Path $logPath) {
                            Get-Content $logPath
                        } else {
                            Write-Host "Log file not found: $logPath"
                        }

                        Write-Host "----------------------------------------"
                        exit 1
                    }
                '''
            }
        }

        stage('Verify HTTP') {
            steps {
                powershell '''
                    Write-Host "Running HTTP verification..."

                    $url = $env:HEALTH_URL
                    $maxAttempts = 12
                    $delaySeconds = 5
                    $success = $false

                    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                        Write-Host "HTTP check attempt $attempt of $maxAttempts..."
                        Write-Host "URL: $url"

                        try {
                            $response = Invoke-WebRequest `
                                -Uri $url `
                                -UseBasicParsing `
                                -TimeoutSec 20 `
                                -MaximumRedirection 5

                            Write-Host "HTTP status: $($response.StatusCode)"

                            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                                $success = $true
                                break
                            }
                        }
                        catch {
                            Write-Host "HTTP verification attempt failed: $($_.Exception.Message)"
                        }

                        if ($attempt -lt $maxAttempts) {
                            Start-Sleep -Seconds $delaySeconds
                        }
                    }

                    if (-not $success) {
                        Write-Host ""
                        Write-Host "Crypto AI is listening, but HTTP verification failed."
                        Write-Host ""
                        Write-Host "Application log:"
                        Write-Host "----------------------------------------"

                        $logPath = Join-Path $env:DEPLOY_DIR $env:APP_LOG

                        if (Test-Path $logPath) {
                            Get-Content $logPath
                        } else {
                            Write-Host "Log file not found: $logPath"
                        }

                        Write-Host "----------------------------------------"
                        exit 1
                    }

                    Write-Host "Crypto AI HTTP verification succeeded."
                '''
            }
        }

        stage('Confirm process remains running') {
            steps {
                powershell '''
                    Write-Host "Confirming that Crypto AI remains running..."
                    Start-Sleep -Seconds 10

                    $listener = Get-NetTCPConnection `
                        -LocalPort ([int]$env:APP_PORT) `
                        -State Listen `
                        -ErrorAction SilentlyContinue

                    if (-not $listener) {
                        Write-Host "Crypto AI stopped after deployment."

                        $logPath = Join-Path $env:DEPLOY_DIR $env:APP_LOG

                        if (Test-Path $logPath) {
                            Get-Content $logPath
                        }

                        exit 1
                    }

                    Write-Host "Crypto AI is still running."
                    Write-Host "PID: $($listener.OwningProcess)"
                '''
            }
        }
    }

    post {
        success {
            echo 'Crypto AI was built, deployed, started, and verified successfully.'
            echo "Local URL: http://localhost:${APP_PORT}"
            echo "External URL: http://YOUR_PUBLIC_SERVER_IP:${APP_PORT}"
            echo "Health URL: ${HEALTH_URL}"
            echo "Log file: ${DEPLOY_DIR}\\${APP_LOG}"
        }

        failure {
            echo 'Crypto AI deployment failed. Attempting rollback...'

            powershell '''
                $deployDir = $env:DEPLOY_DIR
                $appJar = Join-Path $deployDir $env:APP_JAR
                $backupJar = Join-Path $deployDir $env:BACKUP_JAR
                $logPath = Join-Path $deployDir $env:APP_LOG

                $listener = Get-NetTCPConnection `
                    -LocalPort ([int]$env:APP_PORT) `
                    -State Listen `
                    -ErrorAction SilentlyContinue

                if ($listener) {
                    Write-Host "Stopping failed deployment process PID $($listener.OwningProcess)..."
                    Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
                    Start-Sleep -Seconds 3
                }

                if (-not (Test-Path $backupJar)) {
                    Write-Host "No backup JAR exists. Automatic rollback is unavailable."
                    exit 0
                }

                Write-Host "Restoring backup JAR..."
                Copy-Item -Path $backupJar -Destination $appJar -Force

                if (Test-Path $logPath) {
                    Remove-Item $logPath -Force
                }

                Write-Host "Starting restored application..."

                $javaArguments = @(
                    "-jar"
                    $appJar
                    "--server.port=$env:APP_PORT"
                )

                Start-Process `
                    -FilePath "javaw" `
                    -ArgumentList $javaArguments `
                    -WorkingDirectory $deployDir `
                    -RedirectStandardOutput $logPath `
                    -RedirectStandardError $logPath

                Start-Sleep -Seconds 30

                $restoredListener = Get-NetTCPConnection `
                    -LocalPort ([int]$env:APP_PORT) `
                    -State Listen `
                    -ErrorAction SilentlyContinue

                if ($restoredListener) {
                    Write-Host "Rollback succeeded. Restored application is listening on port $env:APP_PORT."
                } else {
                    Write-Host "Rollback failed. Check the application log:"
                    if (Test-Path $logPath) {
                        Get-Content $logPath
                    }
                }
            '''

            echo 'Check Jenkins Console Output.'
            echo "Also check: ${DEPLOY_DIR}\\${APP_LOG}"
        }

        always {
            archiveArtifacts(
                artifacts: 'target/*.jar',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
    }
}
