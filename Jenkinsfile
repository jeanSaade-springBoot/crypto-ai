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

                echo Building Crypto AI ...

                mvn clean package -DskipTests

                if errorlevel 1 (
                    echo Maven build failed.
                    exit /b 1
                )
                '''
            }
        }

        stage('Stop old application') {
            steps {
                bat '''
                @echo off

                echo Checking for an existing Crypto AI process on port %APP_PORT%...

                for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%APP_PORT%" ^| findstr "LISTENING"') do (
                    echo Stopping process PID %%a...
                    taskkill /PID %%a /F
                )

                powershell -NoProfile -Command "Start-Sleep -Seconds 3"

                exit /b 0
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

                if exist "%DEPLOY_DIR%\\%APP_JAR%" (
                    echo Creating backup of the current JAR...

                    copy /Y ^
                        "%DEPLOY_DIR%\\%APP_JAR%" ^
                        "%DEPLOY_DIR%\\%BACKUP_JAR%"

                    if errorlevel 1 (
                        echo Failed to create backup.
                        exit /b 1
                    )
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
                    if /I not "%%~nxf"=="%%~nf.original" (
                        set "FOUND_JAR=%%f"
                    )
                )

                if not defined FOUND_JAR (
                    echo No executable JAR was found in the target folder.
                    exit /b 1
                )

                echo Deploying !FOUND_JAR!...

                copy /Y ^
                    "!FOUND_JAR!" ^
                    "%DEPLOY_DIR%\\%APP_JAR%"

                if errorlevel 1 (
                    echo Failed to copy the application JAR.
                    exit /b 1
                )

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

                powershell -NoProfile -Command "Start-Sleep -Seconds 35"
                '''
            }
        }

        stage('Verify port') {
            steps {
                bat '''
                @echo off

                echo Checking port %APP_PORT%...

                netstat -ano ^
                    | findstr ":%APP_PORT%" ^
                    | findstr "LISTENING"

                if errorlevel 1 (
                    echo.
                    echo Crypto AI did not start successfully.
                    echo.
                    echo Application log:
                    echo ----------------------------------------
                    type "%DEPLOY_DIR%\\%APP_LOG%"
                    echo ----------------------------------------
                    exit /b 1
                )

                echo Crypto AI is listening on port %APP_PORT%.
                '''
            }
        }

        stage('Verify HTTP') {
            steps {
                bat '''
                @echo off

                echo Running HTTP verification...

                powershell -NoProfile -Command ^
                    "try { ^
                        $response = Invoke-WebRequest ^
                            -UseBasicParsing ^
                            -Uri 'http://localhost:%APP_PORT%/' ^
                            -TimeoutSec 20; ^
                        Write-Host ('HTTP status: ' + $response.StatusCode); ^
                        exit 0 ^
                    } catch { ^
                        Write-Host $_.Exception.Message; ^
                        exit 1 ^
                    }"

                if errorlevel 1 (
                    echo Crypto AI is listening, but HTTP verification failed.
                    echo.
                    echo Application log:
                    echo ----------------------------------------
                    type "%DEPLOY_DIR%\\%APP_LOG%"
                    echo ----------------------------------------
                    exit /b 1
                )

                echo Crypto AI HTTP verification succeeded.
                '''
            }
        }

        stage('Confirm process remains running') {
            steps {
                bat '''
                @echo off

                powershell -NoProfile -Command "Start-Sleep -Seconds 5"

                netstat -ano ^
                    | findstr ":%APP_PORT%" ^
                    | findstr "LISTENING"

                if errorlevel 1 (
                    echo Crypto AI stopped after deployment.
                    echo.
                    type "%DEPLOY_DIR%\\%APP_LOG%"
                    exit /b 1
                )

                echo Crypto AI is still running.
                '''
            }
        }
    }

    post {
        success {
            echo 'Crypto AI was built, deployed, started, and verified successfully.'
            echo 'Local URL: http://localhost:8083'
            echo 'External URL: http://YOUR_PUBLIC_SERVER_IP:8083'
            echo 'Log file: C:\\apps\\crypto-ai\\crypto-ai.log'
        }

        failure {
            echo 'Crypto AI deployment failed.'
            echo 'Check Jenkins Console Output.'
            echo 'Also check: C:\\apps\\crypto-ai\\crypto-ai.log'
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