# AI team command catalog

These are plain-language commands. The project now includes executable local scripts for the peer/team commands below; they are still not guaranteed native Codex slash-autocomplete commands.

| Command | One-line hint | Execution |
| --- | --- | --- |
| `team commands` | Show this command menu. | `scripts/team-command.ps1` / read this file |
| `proceed claude` | Ask Claude once and show the actual Claude answer inside direct Codex. | `scripts/team-peer.ps1 -Peer CLAUDE` |
| `proceed claude deep` | Deep Claude peer review with max configured reasoning + public research. | same + `-Deep` |
| `proceed codex` | From Claude, ask Codex once and show the actual answer. | `scripts/team-peer.ps1 -Peer CODEX` |
| `proceed codex deep` | Deep Codex peer review/research. | same + `-Deep` |
| `proceed coordinator` | Handoff current artifact to full multi-round Codex <-> Claude review with live dashboard. | `scripts/team-handoff.ps1` |
| `team status` | Show current live team dashboard. | `agent-docs/LIVE_TEAM_STATUS.md` |
| `team events` | Show chronological events for the active/latest run. | latest `LIVE_EVENTS.log` |
| `investigate <question>` | Formal read-only code + logs + DB + web investigation. | Coordinator |
| `trade <id>` | Full read-only trade investigation with UTC/KSA, Replay and Production evidence. | Coordinator |
| `research <topic>` | Deep public research with citations and limitations. | read-only agents |
| `db <question>` | Bounded MySQL evidence via read-only wrapper. | `scripts/agent-db-query.ps1` |
| `logs <question>` | Targeted local log investigation. | read-only |
| `design` | Draft technical design/change registry; do not implement. | read-only |
| `review` | Review current design/code without editing. | read-only |
| `spec` | Show current FINAL_CHANGE_SPECIFICATION and approval state. | read-only |
| `changes` | Show proposed class/method/log/DB/test registry. | read-only |
| `implement` | Implement exact frozen dual-approved spec only after explicit user authorization. | guarded `implementation-review.ps1` |
| `claude review` | Full Claude review of current complete implementation. | code-review loop |
| `fix review` | Codex addresses accepted Claude findings within approved scope. | code-review loop |
| `validate` | Dual-agent post-implementation validation + frozen validation PDF. | `scripts/validate-implementation.ps1` |
| `registry` | Reconcile actual implementation vs approved registry. | read-only report |
| `memory` | Show dual-approved shared knowledge. | `AGREED_KNOWLEDGE.md` |
| `remember team` | Propose exact durable knowledge for both agents to approve. | dual-agent gate |
| `decisions` | Show approved decisions. | `DECISIONS.md` |
| `peer memory` | Show non-authoritative prior peer positions. | `agent-docs/memory/` |
| `architecture` | Show current implemented architecture; distinguish pending design. | read-only |
| `trading rules` | Show current trading rules and uncertainty. | read-only |
| `freeze artifact` | Generate frozen revision/hash PDF for a review artifact. | `freeze-agent-artifact.ps1` |
| `stop team` | Safely stop the identified team operation; never kill unrelated processes. | guarded |

## Meaning of the three most-used commands

- `proceed claude`: one peer opinion, returned directly to Codex. No consensus or approval.
- `proceed claude deep`: same, but deep review/research.
- `proceed coordinator`: formal multi-round governance, live status, dual approval, frozen review artifact, then user gate.

Mirror behavior applies from Claude with `proceed codex` and `proceed codex deep`.

## Jean formal review commands

| Command | One-line hint |
|---|---|
| `proceed analysis from jean` | Make Jean the formal reviewer; track every Jean comment as `USR-REV-*`; AI approval becomes optional unless Jean requests it. |
| `proceed merging from jean` | Jean authorizes the exact reviewed scope for local implementation after all Jean findings are resolved; no push/deploy/DB writes. |
| `show jean review` | Show current Jean-review mode, reviewed revision/hash, and all `USR-REV-*` findings/statuses. |
| `show jean comments` | Show Jean's open, awaiting, accepted, rejected and waived review findings. |

`proceed merging from jean` is an implementation authorization command, not a Git/remote merge command. It updates only the local working tree unless Jean separately authorizes additional actions.

## Read-only analysis control room (added)

| Command | One-line hint | Execution |
| --- | --- | --- |
| `what are they doing` | Current run, round, active/waiting agents, last recorded events. | `scripts/team-analysis-status.ps1 -Mode Quick` |
| `analysis status` | Concise current progress snapshot; does not interrupt agents. | `scripts/team-analysis-status.ps1 -Mode Quick` |
| `analysis status full` | Separate Codex/Claude findings, agreements, exact DB table names, query IDs, log evidence and gaps. | `scripts/team-analysis-status.ps1 -Mode Full` |
| `analysis history` | Round-by-round completed artifacts and recorded position changes. | `scripts/team-analysis-status.ps1 -Mode History` |
| `/commands` (text input) | Show this entire catalog with one-line hints IF the Codex client forwards it to the agent. | Read `agent-docs/COMMANDS.md` |

**Important:** The Codex extension may intercept `/commands` as a native UI command. Repository instructions cannot override that UI behavior. If `/commands` does not reach the agent, use `team commands` (guaranteed conversational alias) or ask Codex to read `agent-docs/COMMANDS.md`. Do not claim native slash autocomplete is installed.

The read-only observer never launches Codex/Claude, changes state, or changes consensus. In full mode, table names are parsed from persisted `.sql` audit files and labeled as candidates until successful execution is confirmed from the result/audit files. Never claim that a table was checked solely because its name appeared in a prompt. If no log evidence exists, state that explicitly. If an agent is still working, show the latest *completed* findings, not invented mid-turn reasoning.

## Jean owner override (added)

| Command | One-line hint |
|---|---|
| `proceed direct from jean` | Owner override: waive pending reviews/findings/gates for the stated scope and let Codex implement locally now, using its own judgment. No push/deploy/Production DB unless listed under `also allow:`. |
| `end override` | Close the active Jean override; return to normal governance. |
