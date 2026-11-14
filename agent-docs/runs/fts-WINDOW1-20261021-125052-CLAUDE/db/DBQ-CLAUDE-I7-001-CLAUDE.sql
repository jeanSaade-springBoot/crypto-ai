SELECT id, signal_id, LENGTH(entry_reason) AS len,
       INSTR(entry_reason,'recent rejection zone') AS rej_pos,
       INSTR(entry_reason,'Entry Quality') AS eq_pos,
       SUBSTRING(entry_reason, GREATEST(INSTR(entry_reason,'Entry Quality')-1,1), 240) AS eq_excerpt,
       LEFT(entry_reason, 300) AS head_excerpt
FROM paper_position
WHERE id IN (137,143,158,162,172,182)
ORDER BY id