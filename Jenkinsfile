pipeline {
    agent any
    tools { maven 'Maven' }
    options { disableConcurrentBuilds(); timestamps(); buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10')) }
    environment {
        TRADER_JAVA_EXE = 'C:\\Program Files\\Java\\jdk-21.0.12\\bin\\java.exe'
        TRADER_STARTUP_TIMEOUT = '1800'
    }
    stages {
        stage('Checkout') { steps { checkout scm } }
        stage('Build and test') {
            steps {
                script {
                    def javaHome = env.TRADER_JAVA_EXE.replaceAll('(?i)\\\\bin\\\\java\\.exe$', '')
                    withEnv(["JAVA_HOME=${javaHome}", "PATH+TRADER_JAVA=${javaHome}\\bin"]) {
                        bat 'call mvn -B -ntp clean package'
                    }
                }
            }
        }
        stage('Deploy Trader LIVE') {
            steps {
                withEnv(['JENKINS_NODE_COOKIE=crypto-ai-trader-managed']) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        & "$env:WORKSPACE\\scripts\\deploy\\Deploy-Trader.ps1" `
                          -JavaExe $env:TRADER_JAVA_EXE `
                          -ArtifactDirectory "$env:WORKSPACE\\target" `
                          -StartupTimeoutSeconds ([int]$env:TRADER_STARTUP_TIMEOUT)
                        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                    '''
                }
            }
        }
    }
    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar,target/deployment-evidence/**', fingerprint: true, allowEmptyArchive: true
        }
        failure {
            echo 'Build/deployment failed. No automatic rollback, log deletion, or second launch is performed. Inspect the retained PID, logs and Flyway state before recovery.'
        }
    }
}
