-- FIX-145. LOCAL crypto_ai ONLY. STOP ALL TRADER/EXECUTION WRITERS FIRST.
-- V101 and revised Java startup guard must be installed first.
-- This operator decision abandons replay; it DOES NOT establish no past effects.
USE crypto_ai;
SET SESSION time_zone = '+00:00';
SET SESSION innodb_lock_wait_timeout = 10;
SET @cleanup_operator = 'Jean';
SET @cleanup_reference = 'FIX-145-31-LEGACY-20261004';
-- Set to 1 only after stopping all applications that can write trades/work.
SET @cleanup_writers_stopped = 0;

DELIMITER $$
DROP PROCEDURE IF EXISTS fix145_cancel_approved_31$$
CREATE PROCEDURE fix145_cancel_approved_31()
BEGIN
 DECLARE n INT DEFAULT 0;
 DECLARE cancellation_time TIMESTAMP(6);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
   ROLLBACK;
   DROP TEMPORARY TABLE IF EXISTS fix145_selected;
   RESIGNAL;
 END;
 IF DATABASE()<>'crypto_ai' OR @@port<>3307
    OR LOWER(@@hostname)<>'johnnym'
    OR @@server_uuid<>'6d85cf28-dc2d-11ef-99dd-dc97ba70b281' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wrong server/schema: approved local JohnnyM:3307 only';
 END IF;
 IF COALESCE(@cleanup_writers_stopped,0)<>1
    OR COALESCE(LENGTH(TRIM(@cleanup_operator)),0)=0
    OR COALESCE(LENGTH(TRIM(@cleanup_reference)),0)=0 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stop all execution writers and set operator/reference first';
 END IF;
 CREATE TEMPORARY TABLE fix145_selected LIKE signal_processing_work;
 START TRANSACTION;
 INSERT INTO fix145_selected (signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,owner_token,next_attempt_at,updated_at,completed_at,paper_position_id,failure_stage,error_message,source_event_id)
 SELECT signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,owner_token,next_attempt_at,updated_at,completed_at,paper_position_id,failure_stage,error_message,source_event_id FROM signal_processing_work
 WHERE signal_id IN (3432,3536,4058,4227,5307,5378,5400,5434,5484,5631,5638,5653,5686,6246,6366,6407,7860,8065,8110,8501,8764,8849,8976,9041,9128,9517,9524,9862,10068,10132,10169)
 ORDER BY signal_id FOR UPDATE;
 SELECT COUNT(*) INTO n FROM fix145_selected;
 IF n<>31 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly 31 approved work records; nothing cancelled';
 END IF;
 SELECT COUNT(*) INTO n FROM fix145_selected
 WHERE source_event_id IS NULL AND status='REVIEW_REQUIRED'
   AND origin='WORKER' AND attempts=1 AND owner_token IS NULL
   AND completed_at IS NULL AND paper_position_id IS NULL
   AND failure_stage='PROCESSING_OR_COMMIT'
   AND updated_at<'2026-10-03 20:21:00.000000'
   AND candle_open_time<'2026-10-03 20:21:00.000000'
   AND error_message LIKE '%Data too long for column%entry_reason%';
 IF n<>31 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approved attempt state/error changed; nothing cancelled';
 END IF;
 SELECT COUNT(*) INTO n FROM fix145_selected s
 WHERE EXISTS (SELECT 1 FROM wallet_trade t WHERE t.signal_id=s.signal_id)
    OR EXISTS (SELECT 1 FROM paper_position p WHERE p.signal_id=s.signal_id OR p.exit_signal_id=s.signal_id)
    OR EXISTS (SELECT 1 FROM signal_processing_work_cancellation a WHERE a.signal_id=s.signal_id);
 IF n<>0 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Linked trade/position or prior cancellation found; nothing cancelled';
 END IF;
 SET cancellation_time=UTC_TIMESTAMP(6);
 INSERT INTO signal_processing_work_cancellation
 (signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,owner_token,next_attempt_at,updated_at,completed_at,paper_position_id,failure_stage,error_message,source_event_id,cancelled_at,operator_name,review_reference,decision)
 SELECT signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,owner_token,next_attempt_at,updated_at,completed_at,paper_position_id,failure_stage,error_message,source_event_id,cancellation_time,@cleanup_operator,@cleanup_reference,
        'ABANDON_NO_REPLAY_EFFECTS_UNKNOWN' FROM fix145_selected;
 IF ROW_COUNT()<>31 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit insert count mismatch; rollback';
 END IF;
 UPDATE signal_processing_work w JOIN fix145_selected s ON s.signal_id=w.signal_id
 SET w.status='OPERATOR_CANCELLED',w.updated_at=cancellation_time
 WHERE w.status=s.status AND w.updated_at=s.updated_at AND w.owner_token IS NULL
   AND w.source_event_id IS NULL AND w.completed_at IS NULL;
 IF ROW_COUNT()<>31 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cancellation count mismatch; rollback';
 END IF;
 COMMIT;
 DROP TEMPORARY TABLE fix145_selected;
 SELECT '31 ATTEMPTS CANCELLED; NO REPLAY; PAST EFFECTS NOT CERTIFIED' AS result,
        cancellation_time AS cancelled_utc,
        DATE_ADD(cancellation_time,INTERVAL 3 HOUR) AS cancelled_riyadh;
END$$
DELIMITER ;
CALL fix145_cancel_approved_31();
DROP PROCEDURE fix145_cancel_approved_31;

SELECT w.signal_id,w.symbol,w.status,w.completed_at,a.operator_name,a.review_reference,
       a.decision,a.cancelled_at,a.status AS original_status,a.error_message AS original_error
FROM signal_processing_work w JOIN signal_processing_work_cancellation a USING(signal_id)
WHERE a.review_reference=@cleanup_reference ORDER BY w.signal_id;
