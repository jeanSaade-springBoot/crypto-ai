SELECT 'wallet_position_test' AS t, COUNT(*) AS n FROM crypto_ai.wallet_position_test
UNION ALL SELECT 'analysis_test_run', COUNT(*) FROM crypto_ai.analysis_test_run
UNION ALL SELECT 'replay_run_output', COUNT(*) FROM crypto_ai.replay_run_output
UNION ALL SELECT 'replay_decision_trace', COUNT(*) FROM crypto_ai.replay_decision_trace
UNION ALL SELECT CONCAT('wmp_eq_', entry_signal_id), entry_quality_score FROM crypto_ai.wallet_managed_position
          WHERE entry_signal_id IN (176710,171589,167668,165412,159408,156723);