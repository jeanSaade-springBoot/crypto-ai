# FIX-153 deployment script correction

Merge this `scripts/` directory into the FIX-153 implementation project. The attachment contains scripts only; it is not a replacement source project or executable JAR.

`deploy/Deploy-Trader.ps1` now forces local OFF mode, disables FIX-142 scheduling/acknowledgment and old scheduled analysis, and uses a stable absolute instance-lock path. It checks for V106, B105 and LocalTraderInstance resources in the executable JAR before stopping the existing trader. The process record includes launch settings, exclusion scope, UTC and explicitly offset KSA startup times. Startup verification requires the launched PID, listener, Boot marker and OFF instance-lock marker. It does not certify ingestion, fresh signals, wallet protection or MySQL/load acceptance.

## Run from Command Prompt (example paths)

Use an elevated shell on the deployment host, after the FIX-153 build and required release gates. Confirm your real Java 21 path and that the application DB configuration points to crypto_ai. This command DOES stop recognized existing deployed trader processes and launch the staged JAR.

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\workspace\crypto-ai-trader-mysql\scripts\deploy\Deploy-Trader.ps1" -JavaExe "C:\Program Files\Java\jdk-21\bin\java.exe" -ArtifactDirectory "C:\workspace\crypto-ai-trader-mysql\target" -DeployDirectory "C:\apps\crypto-ai" -InstanceLockPath "C:\apps\crypto-ai\logs\crypto-ai-trader.instance.lock"
```

Without `-InstanceLockPath`, the script uses `DeployDirectory\logs\crypto-ai-trader.instance.lock`. All service, Eclipse and manual starts against this database must use the SAME absolute lock path. Use `LOCAL_TRADER_INSTANCE_LOCK` or `--local-candle.instance-lock-path` for those starts. The deployment mutex is host-only and is released after deployment; the application holds its file lock until JVM exit.

## Required deployment topology

Exactly one trader per database, across every host/container. One replica with no overlapping rolling deployment; old PID must exit first. This script does not provide distributed ownership/fencing, does not discover remote writers and only stops the recognized deployed JARs under its deployment root. Eclipse/other launchers and any collector writing the same candle schema must be stopped separately. An unrecognized process owning the requested port causes refusal rather than termination.

When replacing a running process, `-DrainCommand` is mandatory. It must make an authenticated **ADMIN** `POST /api/local/drain` request to the old Trader and return only once it receives HTTP 202. That endpoint first stops new kline admission, then waits for input callbacks, detector reservations, queued analysis and active analysis to finish before it closes the application context and exits the JVM. The script waits up to 120 seconds (the application drain budget is 60 seconds) for the old PID to exit and refuses to launch a replacement on timeout. It never calls `Stop-Process -Force`.

The caller must use the deployment environment's actual authentication mechanism; this repository does not provide or log credentials. Price protection is paused while drain is active, so keep this window short and use it only for controlled replacement. If the drain itself times out, the application resumes admission; the script then times out waiting for JVM exit and leaves the old process running. Prove the exact command, including its authentication and Windows behaviour, in the deployment rehearsal. A non-202 response, timeout or old process that remains alive is a failed deployment and leaves the existing process running.

The existing immutable-release, checksum, stop/wait, timeout/no-auto-rollback and bounded log-tail behavior remains. Existing legacy SQL/diagnostic/validation scripts are preserved byte-for-byte; they are not executed by deployment. In particular, Verify-Fix142 and collector/cutover SQL remain legacy shared-mode tools, not FIX-153 acceptance gates. Do not run every script in the folder as a batch.

## Validation performed

Static checks verify the exact OFF arguments, stable lock argument, OFF evidence/marker, required artifact resources before any replacement, and preservation of every other uploaded script. The corrected ZIP passes archive integrity checking. No PowerShell interpreter is available in this environment: Windows PowerShell 5.1 parsing, process handling and actual deployment have NOT been executed. Application Maven results from the implementation package do not count as deployment-script validation. No database or running service was changed.
