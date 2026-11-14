# Direct Agent Handoff Guide

## Codex design -> Coordinator -> Claude review

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\agent-docs\coordinator.ps1 `
  -ProjectRoot "C:\workspace\crypto-ai-trader-mysql" `
  -Question "Review the handed-off technical design and converge on an agreed specification. Do not implement." `
  -HandoffFrom CODEX `
  -HandoffType TECHNICAL_DESIGN `
  -HandoffFile ".\codex-docs\FIX-XXX_TECHNICAL_DESIGN.md"
```

## Claude investigation -> Coordinator

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\agent-docs\coordinator.ps1 `
  -ProjectRoot "C:\workspace\crypto-ai-trader-mysql" `
  -Question "Continue this investigation through dual-agent adversarial review. Read-only." `
  -HandoffFrom CLAUDE `
  -HandoffType INVESTIGATION `
  -HandoffFile ".\claude-docs\INVESTIGATION_LOG.md"
```

## Code review handoff

Use `-HandoffType CODE_REVIEW` and point `-HandoffFile` at the implementation/review artifact. Existing governance still applies: Codex implements, Claude reviews, Codex corrects, and Claude re-reviews the complete implementation. The handoff does not authorize code changes by itself.

## Visibility

The dashboard shows `Origin: DIRECT CODEX HANDOFF` or `Origin: DIRECT CLAUDE HANDOFF`, handoff type, current phase/round, current activities, evidence, disagreements, and next action. Original handoff bytes are copied into the run's `handoff/ORIGINAL_CONTEXT.md` for traceability.

## Executable direct commands

From direct Codex:
- `proceed claude` -> run `scripts/team-peer.ps1 -Peer CLAUDE ...` and display the real captured Claude output in Codex.
- `proceed claude deep` -> same with `-Deep`.
- `proceed coordinator` -> run `scripts/team-handoff.ps1 -From CODEX ...` with the current artifact. The live dashboard starts at handoff.

From direct Claude, mirror with `proceed codex`, `proceed codex deep`, and `proceed coordinator -From CLAUDE`.

The one-shot peer command is not consensus. Formal approval always requires the Coordinator.
