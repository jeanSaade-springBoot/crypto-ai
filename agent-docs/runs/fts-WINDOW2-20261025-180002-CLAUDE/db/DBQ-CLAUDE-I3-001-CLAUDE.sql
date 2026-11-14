SELECT @@session.time_zone AS session_tz, @@global.time_zone AS global_tz, @@system_time_zone AS system_tz, NOW(6) AS now_session, UTC_TIMESTAMP(6) AS now_utc,
 (SELECT UNIX_TIMESTAMP(opened_at) FROM crypto_ai.paper_position WHERE id = 143) AS p143_opened_epoch,
 (SELECT UNIX_TIMESTAMP(observed_at) FROM crypto_ai.market_price_event WHERE id = 1789409) AS tick_1789409_epoch,
 (SELECT UNIX_TIMESTAMP(execution_price_observed_at) FROM crypto_ai.wallet_trade WHERE id = 451) AS wt451_obs_epoch
LIMIT 1;