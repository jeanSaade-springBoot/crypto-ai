# Setup in Cursor (Windows)

1. Copy `agent-docs/coordinator.ps1` and `agent-docs/COORDINATOR.md` into your existing project `agent-docs/` folder.
2. Copy `.cursor/rules/ai-team-coordinator.mdc` into your project's `.cursor/rules/` folder (create directories if needed).
3. Keep your existing `AGENTS.md`, `codex-docs/`, `claude-docs/`, and all source files unchanged.
4. In Cursor, open the LEFT Agent panel. Ask it: `Read AGENTS.md, agent-docs/COORDINATOR.md, and .cursor/rules/ai-team-coordinator.mdc. Run the read-only coordinator for: Explain the candle ingestion architecture. Do not change code.`
5. Or from the project PowerShell terminal run:
   `powershell -NoProfile -File .\agent-docs\coordinator.ps1 -ProjectRoot 'C:\workspace\crypto-ai-trader-mysql' -Question 'Explain the candle ingestion architecture' -MaxRounds 12`
6. Watch terminal progress. Open `agent-docs/runs/<timestamp>/PROGRESS.log` and individual round files for evidence.

IMPORTANT: This is a read-only proof-of-concept, not a production-ready fully autonomous debate manager. It runs sequentially, not concurrently. It caps rounds to avoid unbounded cost. It does not automatically revise rejected final documents or implement code. A second stage is needed for that workflow. Do not use an unlimited loop without budgets, timeouts and human stop controls.

Codex extension binary and Claude extension binary are discovered from `%USERPROFILE%\.cursor\extensions` at runtime; no PATH changes required.
