SELECT p.id AS paper_id, ev.*
FROM paper_position p
JOIN position_management_event ev
  ON ev.symbol = p.symbol
 AND ev.occurred_at BETWEEN p.opened_at - INTERVAL 5 SECOND AND p.closed_at + INTERVAL 5 SECOND
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, ev.occurred_at
LIMIT 200;
SELECT w.*
FROM signal_processing_work w
WHERE w.signal_id IN (176710,171589,167668,165412,159408,156723)
   OR w.paper_position_id IN (182,172,162,158,143,137)