# FIX-142 development candidate v3

Full source, migration V99, patch and local verifier for crypto-ai-trader. This is a development candidate, not a deployment-approved release.

Read the canonical implementation/validation document: [FIX-142.md](src/main/resources/md/FIX-142.md).

Read the consolidated response to every v2 review comment and operator recovery instructions: [REVIEW-RESPONSE-FIX-142-v3.md](REVIEW-RESPONSE-FIX-142-v3.md).

V3 closes the missing operator-resolution path for protection fences. It never automatically releases uncertainty or replays an old price. Scheduling, acknowledgment and manual resolution stay off by default.

Run from this extracted directory in PowerShell:

```powershell
.\scripts\Verify-Fix142.ps1 -JavaHome 'C:\Program Files\Java\jdk-21'
```

This command builds/tests only. For real MySQL validation, follow the canonical document and use a dedicated disposable test instance. No migration was executed or production database accessed by the author.

Validation: v1 previously passed 26 isolated Java21/H2 tests; v2 diagnostics had a limited Java17/proxy smoke pass. V3 has seven new regression tests, unexecuted here; changed Java syntax parses. Full Java21 build, actual MySQL, sustained backlog, Production/Replay, protective-exit and logging-overhead acceptance remain open. Keep the candidate's new scheduling disabled until these pass.
