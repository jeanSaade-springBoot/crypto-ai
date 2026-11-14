# JEAN FORMAL REVIEW RULES

## Purpose
Jean may act as the sole formal reviewer for a task while retaining optional access to Claude and the full Coordinator team.

## Start review
Command: `proceed analysis from jean`

This activates `JEAN_REVIEW` for the current task/revision. Every subsequent Jean review comment becomes `USR-REV-NNN` and must be preserved with its exact wording and disposition.

## Finding lifecycle
`OPEN` -> `ADDRESSED - AWAITING JEAN` -> one of `ACCEPTED BY JEAN`, `REJECTED BY JEAN`, `WAIVED BY JEAN`.

Only Jean may move a finding to a final state. Codex/Claude may respond, implement corrections, provide evidence, or dispute a finding, but cannot close it themselves.

## Implementation authorization
Command: `proceed merging from jean`

Prerequisites:
- JEAN_REVIEW applies to the current task.
- exact reviewed revision/scope is identifiable;
- zero OPEN findings;
- zero ADDRESSED - AWAITING JEAN findings;
- required code/logging/tests/validation/registry scope is known.

Authorization is for local working-tree implementation only. No push, remote merge, deployment, Production change, DB write, or unrelated refactoring is authorized.

If implementation needs a material deviation, stop and return to Jean review.

## Owner override
`proceed direct from jean` skips the finding lifecycle above for the stated
scope: listed or all pending findings become `WAIVED BY JEAN` and Codex
implements locally at once. See AGENTS.md, "JEAN DIRECT AUTHORITY OVERRIDE".
