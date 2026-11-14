# Team command setup

The command layer is local and additive. Keep these files in the repository:
- `scripts/team-peer.ps1`
- `scripts/team-handoff.ps1`
- `scripts/team-command.ps1`
- `scripts/freeze-agent-artifact.ps1`
- `scripts/implementation-review.ps1`
- `scripts/validate-implementation.ps1`
- `.cursor/rules/ai-team-coordinator.mdc`

Direct Codex/Claude reads AGENTS.md and the Cursor rule. When the user says `proceed claude`, `proceed claude deep`, `proceed codex`, `proceed codex deep`, or `proceed coordinator`, invoke the corresponding local script and return the real result. Never simulate a peer call.

Example direct Codex one-shot Claude review:
`powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\team-peer.ps1 -Peer CLAUDE -Question "Review the current proposal" -Artifact ".\\agent-docs\\FINAL_CHANGE_SPECIFICATION.md"`

Example formal handoff:
`powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\team-handoff.ps1 -From CODEX -Type TECHNICAL_DESIGN -Question "Review the current design" -Artifact ".\\agent-docs\\FINAL_CHANGE_SPECIFICATION.md"`

Implementation automation is intentionally blocked unless `-UserAuthorized` is explicitly supplied.
