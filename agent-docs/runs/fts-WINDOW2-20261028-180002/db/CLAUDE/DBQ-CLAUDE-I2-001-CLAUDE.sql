SELECT @@global.time_zone AS g_tz, @@session.time_zone AS s_tz, @@system_time_zone AS sys_tz,
       NOW(6) AS now_session, UTC_TIMESTAMP(6) AS now_utc,
       TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW()) AS session_offset_s;
SELECT id, symbol, opened_at, closed_at,
       UNIX_TIMESTAMP(opened_at) AS opened_epoch, UNIX_TIMESTAMP(closed_at) AS closed_epoch
FROM paper_position WHERE id IN (182,172,162,158,143,137) ORDER BY id DESC;