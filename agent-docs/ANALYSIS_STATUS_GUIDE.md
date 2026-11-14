# Analysis control-room commands

Use in direct Codex, Claude, or Cursor Agent as natural-language instructions:

- `what are they doing` — brief status and last recorded events.
- `analysis status full` — detailed Codex versus Claude positions, DB tables, SQL files, logs, evidence gaps.
- `analysis history` — all completed rounds and position changes.
- `team commands` — full one-line command catalog.
- `/commands` — requests the same catalog **only if the client forwards this text**; native slash menus cannot be overridden by this repository.

Direct PowerShell 5.1 equivalents (run from repository root):

```powershell
powershell -NoProfile -File .\scripts\team-analysis-status.ps1 -Mode Quick
powershell -NoProfile -File .\scripts\team-analysis-status.ps1 -Mode Full
powershell -NoProfile -File .\scripts\team-analysis-status.ps1 -Mode History
```

The observer is read-only, and does not contact either model. Results are limited to completed and persisted run evidence. `Full` reports exact SQL table names extracted from audit SQL files; confirm actual successful execution in corresponding results. It does not guess unlogged DB queries or logs.
