# Frozen PDF Artifact Rules

Markdown remains the machine-readable source. A PDF is the frozen human-review artifact for major approval gates.

Mandatory frozen artifacts when the corresponding stage exists:
- `FINAL_TECHNICAL_REVIEW-Rxx.pdf`
- `FINAL_CHANGE_SPECIFICATION-Rxx.pdf`
- `IMPLEMENTATION_REPORT-Rxx.pdf`
- `VALIDATION_REPORT-Rxx.pdf`

Each frozen PDF must include the source revision, SHA-256 of the exact source Markdown, Codex verdict, Claude verdict, implementation authorization state, and a statement that any material source change invalidates the frozen revision.

Use `scripts/freeze-agent-artifact.ps1`. A dual-approved specification is not READY FOR USER REVIEW until its PDF freeze succeeds. PDF generation never grants implementation authorization.
