# FIX-135 — Automatic LIVE deployment configuration

User-approved deployment target: LIVE. No Jenkins build parameters or deployment checkbox. Existing SCM trigger configuration is unchanged. Maven tests must pass before deployment. Replace Jenkinsfile, scripts/deploy/Deploy-Trader.ps1 and DeploymentSafetyContractTest.java together. If Jenkins uses an inline pipeline, replace that definition too; changing only the repository Jenkinsfile does not update an inline pipeline.

The CLI requests shared-market.mode=LIVE and shared-market.activation-approved=true. These override YAML. The latter records activation intent only: it does not approve database boundaries. All existing application checks, operator approval metadata and audit validation remain unchanged. This patch does not write or approve cutover state, rename candle, modify migrations, fix collector classification or change trading formulas.

Before running this deployment: complete the audited cutover procedure and unresolved collector classification verification, configure the shared source URL and credentials, and reconcile unfinished work as required by FIX-132. Otherwise startup can fail after the prior Trader has been stopped, leaving Trader unavailable. This package is not evidence that those activation prerequisites passed.

FIX-135 safeguards remain: immutable release JAR, explicit Java 21.0.12 executable, process identity and port verification, retained logs, deployment mutual exclusion, and no automatic rollback or second launch. Startup verification is not a business-readiness certification.

Validation: Python source checks passed for consistent LIVE arguments, no parameter gating, test-before-deploy ordering and retained safeguards. Maven/JUnit and Windows execution have not been run for this update. Run Jenkins with tests enabled and review its actual results.
