SELECT id, CHAR_LENGTH(entry_reason) AS entry_len, LEFT(entry_reason, 3000) AS entry_reason_head, CHAR_LENGTH(exit_reason) AS exit_len, LEFT(exit_reason, 2000) AS exit_reason_head
FROM crypto_ai.paper_position
WHERE id IN (182,172,162,158,143,137)
ORDER BY id DESC
LIMIT 6;