SELECT *
FROM trade_signal
WHERE id IN (
  SELECT signal_id FROM paper_position WHERE id IN (182, 172, 162, 158, 143, 137)
  UNION
  SELECT exit_signal_id FROM paper_position WHERE id IN (182, 172, 162, 158, 143, 137) AND exit_signal_id IS NOT NULL
);