SELECT pp.*
FROM crypto_ai.paper_position AS pp
WHERE pp.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(pp.id, 182, 172, 162, 158, 143, 137);