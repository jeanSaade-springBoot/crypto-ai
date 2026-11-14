# FINAL CHANGE SPECIFICATION — TEMPLATE

> This is a template. A generated specification is NOT implementation-ready
> until Codex and Claude approve the same exact revision and the user explicitly
> authorizes implementation.

Revision: DRAFT
Status: NOT APPROVED
Implementation authorization: NOT AUTHORIZED

## 1. Business objective
Current business behavior:

Required business behavior:

Why the change is required:

## 2. Agreed business flow
### Current flow

### Agreed target flow
1.

## 3. Confirmed root cause and evidence
Confirmed root cause:

Contributing factors:

Evidence:

Rejected hypotheses:

Remaining unknowns:

## 4. Scope
### In scope

### Out of scope

## 5. Complete class/file change registry
| ID | Module | File/Class | Change Type | Reason | Business Flow Step |
|---|---|---|---|---|---|

Allowed change types: MODIFY, CREATE, DELETE, RENAME, CONFIG, MIGRATION, TEST, DOCUMENTATION.

## 6. Class-by-class technical specification
For every registered implementation file/class include:

### [Registry ID] File/Class
- File:
- Class:
- Current responsibility:
- Current behavior:
- Required responsibility:
- Required change:
- Methods affected:
- Logic before:
- Logic after:
- Business reason:
- Inputs:
- Outputs:
- Side effects:
- Error handling:
- Concurrency/transaction impact:
- Replay impact:
- Production impact:
- Tests required:

## 7. Method-level change registry
| ID | Class | Method | Current Logic | Agreed New Logic | Reason |
|---|---|---|---|---|---|

## 8. Logging registry
All materially affected areas require an explicit logging decision.

| ID | Class | Method/Area | Level | Event | Required Safe Context | Business Flow Step |
|---|---|---|---|---|---|---|

Logging requirements:
- make the affected business flow and decisions reconstructable;
- include useful correlation/trade/symbol/interval/candle/test identifiers where applicable;
- respect UTC storage/backend and KSA display/audit rules;
- never log passwords, secrets, tokens or credentials;
- avoid context-free logs that cannot support investigation.

## 9. Database change registry
| ID | Object | Change | Reason | Migration | Rollback |
|---|---|---|---|---|---|

If none: `DATABASE CHANGES: NONE`.

## 10. Configuration registry
| ID | Property | Current | Proposed | Reason | Environment Impact |
|---|---|---|---|---|---|

If none: `CONFIGURATION CHANGES: NONE`.

## 11. Trading-behavior registry
Explicitly cover entry, exit, SL/TP/trailing, scoring, vetoes, indicators,
opportunity health, market context, candle handling and position management.

| ID | Rule/Area | Current Rule | Agreed New Rule | Reason | Replay | Production |
|---|---|---|---|---|---|---|

If none: `TRADING BEHAVIOR CHANGE: NONE`.

## 12. Test registry
| ID | Requirement/Registry IDs | Test Type | Scenario | Expected Result |
|---|---|---|---|---|

Include positive, negative, edge, regression, failure/recovery, concurrency
where relevant, Replay and Production-safe validation.

## 13. Observability and validation registry
| ID | Requirement | Evidence/Metric/Log/SQL | Success Criterion |
|---|---|---|---|

## 14. Risks
| Risk | Severity | Probability | Mitigation | Validation |
|---|---|---|---|---|

## 15. Rollback plan
Code rollback:

Database rollback:

Configuration rollback:

Deployment rollback:

Data compatibility:

## 16. Documentation registry
| ID | Document | Required Update | When |
|---|---|---|---|

Consider relevant files under `codex-docs/`, `claude-docs/`,
`agent-docs/AGREED_KNOWLEDGE.md` and `agent-docs/DECISIONS.md`.

## 17. Master implementation registry
| ID | Type | Artifact | Agreed Change | Status |
|---|---|---|---|---|

Every implementation artifact must have a registry ID. Initial status is PLANNED.

## 18. Dual-agent final review
Codex verdict: NOT REVIEWED
Codex notes:

Claude verdict: NOT REVIEWED
Claude notes:

Unresolved material disagreements:

Coordinator status: CONSENSUS NOT ACHIEVED
Implementation authorization: NOT AUTHORIZED

## 19. User approval
User decision: NOT YET PROVIDED
Approved exact revision:

No implementation may begin until this section records explicit user approval.
