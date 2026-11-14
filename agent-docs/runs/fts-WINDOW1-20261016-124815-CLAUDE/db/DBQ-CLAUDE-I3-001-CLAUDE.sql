SELECT @@global.time_zone AS g_tz, @@session.time_zone AS s_tz, @@system_time_zone AS sys_tz,
       NOW(6) AS now_session, UTC_TIMESTAMP(6) AS now_utc,
       (SELECT UNIX_TIMESTAMP(observed_at) FROM crypto_ai.market_price_event WHERE id=1788985) AS epoch_1788985,
       (SELECT UNIX_TIMESTAMP(observed_at) FROM crypto_ai.market_price_event WHERE id=1789409) AS epoch_1789409,
       (SELECT UNIX_TIMESTAMP(closed_at) FROM crypto_ai.paper_position WHERE id=143) AS epoch_pp143_closed;