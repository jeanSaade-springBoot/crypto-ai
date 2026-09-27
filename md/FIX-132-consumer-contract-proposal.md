# FIX-132 approved consumer contracts

The filename is retained for existing links. This document supersedes its earlier proposal status. The user explicitly approved current-committed candle versions with fingerprints and the new freshness gates. Implementation details and measured-test limits are in FIX-132.md.

- Ordering: transactional per-symbol committed sequence; separate original observation time and per-interval high-water. Delayed repair/history never becomes live solely by sequence.
- Fencing: local symbol/event row locks through each phase commit; durable START before protection; uncertain started protection/analysis requires reconciliation, not automatic re-execution. FIX-125 remains separate.
- Replay: immutable price provenance, applied/legacy evidence only, no live claims. Fingerprints are not archived candle versions. No new uninterrupted-delivery simulation mode is claimed.
- Versions: exact current committed candle identity; no nearest fallback or generated_at identity substitution.
- Freshness: canonical 1m protection <=15s; live-close grace 90/120/300/600/1800 seconds for 1m/5m/1h/4h/1d, 120 seconds otherwise. Source, order, future-time and cutover checks also apply.
- Cutover: per-symbol committed sequence plus explicit UTC time. Reconcile old-path execution independently. Events at/below boundary cannot execute; higher sequences still require source/order/freshness checks.

No deployment was performed. Retention, measured operational acceptance and MySQL-specific validation remain activation gates.
