# FIX-113A — Dashboard Signal View Chart isolation

## Scope
UI/read-only performance correction only. The Replay = Production golden rule is unchanged.

## Problem
Dashboard Signals `View chart` used a normal `/dashboard?...` navigation. The browser reloaded the page, which rebuilt/refetched the Signals grid even though only the chart was required. The chart anchor also shared the `signal-detail-button` styling class and was accidentally included in the analysis-toggle event binding.

## Change
- Analysis-toggle binding now targets only real `button.signal-detail-button` elements with signal/detail IDs.
- Signal `View chart` is intercepted in-page.
- Only `/api/dashboard/chart` is requested with the existing bounded focus window.
- The existing Dashboard chart is rendered and the selected signal is highlighted.
- The click does not call `refreshDashboard()` and does not request `/api/dashboard/overview`.
- Existing deep-link href is retained as a non-JavaScript fallback.

## Trading / Replay impact
None. No Production or Replay decision, signal, execution, wallet, scoring, threshold, parity, or persistence behavior is changed.
