# FIX-108 — Signal View chart-first loading

- Dashboard deep-link chart loading now starts before the shared Signals grid API.
- The lightweight `/api/dashboard/chart` request is prioritized and a loading label is rendered immediately.
- Signals and full overview hydrate independently.
- No trading or Replay behavior changed.
