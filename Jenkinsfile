pipeline {
    agent any
    tools { maven 'Maven' }
    options { disableConcurrentBuilds(); timestamps() }
    parameters {
        booleanParam(name: 'DEPLOY_TRADER', defaultValue: false,
            description: 'Build/tests only by default. Enable only after the V89 schema/history incident is reconciled and reviewed.')
        string(name: 'JAVA_EXE', defaultValue: 'C:\\Program Files\\Java\\jdk-21.0.12\\bin\\java.exe',
            description: 'Absolute real JDK java.exe, not Oracle javapath or javaw. Verify this path on the agent.')
        string(name: 'STARTUP_TIMEOUT_SECONDS', defaultValue: '1800',
            description: 'Startup observation limit. Timeout NEVER rolls back or replaces a running JAR.')
    }
    stages {
        stage('Checkout') { steps { checkout scm } }
        stage('Build and test') {
            steps {
                script {
                    def javaHome = params.JAVA_EXE.replaceAll('(?i)\\\\bin\\\\java\\.exe$', '')
                    withEnv(["JAVA_HOME=${javaHome}", "PATH+TRADER_JAVA=${javaHome}\\bin"]) {
                        bat 'call mvn -B -ntp clean package'
                    }
                }
            }
        }
        stage('Deploy Trader OFF') {
            when { expression { params.DEPLOY_TRADER } }
            steps {
                withEnv(['JENKINS_NODE_COOKIE=crypto-ai-trader-managed',
                         "TRADER_JAVA_EXE=${params.JAVA_EXE}",
                         "TRADER_STARTUP_TIMEOUT=${params.STARTUP_TIMEOUT_SECONDS}"]) {
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
