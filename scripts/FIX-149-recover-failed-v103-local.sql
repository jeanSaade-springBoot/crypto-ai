-- FIX-149 exceptional recovery ONLY for the failed pre-DDL V103 shown in the
-- 2026-10-05 01:27 KSA startup log. Merge/build the corrected package first.
-- Keep the trader stopped. Run this manually in Workbench on JohnnyM:3307.
-- This is a narrow metadata repair, not a general Flyway repair procedure.
-- The supplied log proves the table check failed BEFORE any ALTER ran.
-- Save the SELECT result as the record of the failed attempt before deletion.
USE crypto_ai;
START TRANSACTION;
SELECT * FROM flyway_schema_history
WHERE installed_rank=103 AND version='103'
  AND description='fix 149 fresh admission indexes' AND success=0
FOR UPDATE;

-- Only the exact failed local entry is removed. Successful V103 entries and all
-- other versions remain untouched. The source table must be in its confirmed
-- schema and absent from the erroneous original index target.
DELETE FROM flyway_schema_history
WHERE installed_rank=103 AND version='103'
  AND description='fix 149 fresh admission indexes' AND success=0
  AND LOWER(@@hostname)='johnnym' AND @@port=3307 AND DATABASE()='crypto_ai'
  AND EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='crypto_ai_v2' AND table_name='market_data_stream_event'
      AND engine='InnoDB'
  )
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='crypto_ai' AND table_name='market_data_stream_event'
  );
SELECT ROW_COUNT() AS failed_v103_entries_removed; -- must be exactly 1
COMMIT;
-- If count is 0, stop and inspect; do not broaden predicates/delete other history.
SELECT installed_rank,version,description,success
FROM flyway_schema_history WHERE version='103';
-- After recovery this returns no rows. Restart ONLY the corrected package to
-- apply V103; success=1 should then appear and both source indexes should exist.
