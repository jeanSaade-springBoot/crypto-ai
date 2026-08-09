ALTER TABLE analysis_test_run
    ADD COLUMN initial_test_capital DECIMAL(20,8) NOT NULL DEFAULT 10000.00000000 AFTER end_time,
    ADD COLUMN simulated_trade_count INT NOT NULL DEFAULT 0 AFTER generated_strong_sell_count,
    ADD COLUMN simulated_win_count INT NOT NULL DEFAULT 0 AFTER simulated_trade_count,
    ADD COLUMN simulated_loss_count INT NOT NULL DEFAULT 0 AFTER simulated_win_count,
    ADD COLUMN simulated_realized_pnl DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_loss_count,
    ADD COLUMN simulated_final_wallet DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_realized_pnl;
ALTER TABLE execution_opportunity_test
    ADD COLUMN replay_stage VARCHAR(30) NULL AFTER generated_at,
    ADD COLUMN evidence_count INT NOT NULL DEFAULT 0 AFTER replay_stage,
    ADD COLUMN buy_count INT NOT NULL DEFAULT 0 AFTER evidence_count,
    ADD COLUMN watch_count INT NOT NULL DEFAULT 0 AFTER buy_count,
    ADD COLUMN neutral_count INT NOT NULL DEFAULT 0 AFTER watch_count,
    ADD COLUMN bearish_count INT NOT NULL DEFAULT 0 AFTER neutral_count,
    ADD COLUMN evidence_score INT NOT NULL DEFAULT 0 AFTER bearish_count,
    ADD COLUMN opportunity_health INT NOT NULL DEFAULT 0 AFTER evidence_score,
    ADD COLUMN recommended_position_percent INT NOT NULL DEFAULT 0 AFTER opportunity_health;
CREATE TABLE wallet_execution_test (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, test_run_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 side VARCHAR(10) NOT NULL, execution_time TIMESTAMP(6) NOT NULL, execution_price DECIMAL(30,12) NOT NULL,
 quantity DECIMAL(30,12) NOT NULL, notional_usdt DECIMAL(20,8) NOT NULL, position_percent INT NOT NULL DEFAULT 0,
 signal_interval VARCHAR(10) NULL, signal_decision VARCHAR(30) NULL, execution_source VARCHAR(60) NULL,
 execution_code VARCHAR(100) NULL, execution_reason VARCHAR(2000) NULL, realized_pnl_usdt DECIMAL(20,8) NULL,
 realized_pnl_percent DECIMAL(20,8) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_wallet_execution_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
 INDEX idx_wallet_execution_test_run_time (test_run_id, execution_time));
CREATE TABLE wallet_position_test (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, test_run_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL, entry_time TIMESTAMP(6) NOT NULL, entry_price DECIMAL(30,12) NOT NULL,
 quantity DECIMAL(30,12) NOT NULL, total_cost_usdt DECIMAL(20,8) NOT NULL, position_percent INT NOT NULL,
 stop_loss_usdt DECIMAL(30,12) NULL, take_profit_usdt DECIMAL(30,12) NULL, highest_price_usdt DECIMAL(30,12) NULL,
 profit_lock_active TINYINT(1) NOT NULL DEFAULT 0, profit_lock_price_usdt DECIMAL(30,12) NULL,
 exit_time TIMESTAMP(6) NULL, exit_price DECIMAL(30,12) NULL, exit_reason VARCHAR(100) NULL,
 exit_explanation VARCHAR(2000) NULL, realized_pnl_usdt DECIMAL(20,8) NULL, realized_pnl_percent DECIMAL(20,8) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_wallet_position_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
 INDEX idx_wallet_position_test_run_time (test_run_id, entry_time));
ALTER TABLE analysis_test_result
    ADD COLUMN simulated_trades INT NOT NULL DEFAULT 0 AFTER generated_signal_errors,
    ADD COLUMN simulated_wins INT NOT NULL DEFAULT 0 AFTER simulated_trades,
    ADD COLUMN simulated_losses INT NOT NULL DEFAULT 0 AFTER simulated_wins,
    ADD COLUMN simulated_realized_pnl DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_losses,
    ADD COLUMN simulated_final_wallet DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_realized_pnl;
