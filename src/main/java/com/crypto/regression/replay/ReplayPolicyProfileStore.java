package com.crypto.regression.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/** FIX-156: Replay-only persistence for an immutable historical policy profile. */
@Repository
public class ReplayPolicyProfileStore {
    private final JdbcTemplate jdbc;

    public ReplayPolicyProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReplayPolicyProfile require(long runId) {
        // FIX-156: never fall back to wallet_settings or current administration values.
        ReplayAccessPolicy.require("replay_policy_profile", ReplayAccessPolicy.Operation.REPLAY_READ);
        ReplayPolicyProfile profile = jdbc.query("""
                SELECT p.run_id, p.profile_hash, p.effective_at, p.dynamic_profit_lock_enabled,
                       p.profit_lock_activation_percent, p.profit_lock_initial_percent,
                       p.profit_lock_trail_step_percent, p.fixed_notional_usdt, d.policy_fingerprint,
                       d.execution_profile, d.require_new_buy_transition, d.minimum_usdt_reserve,
                       d.base_trade_amount_usdt, d.maximum_daily_new_positions,
                       d.exchange_constraint_fixture_hash, d.minimum_executable_notional_usdt
                FROM replay_policy_profile p
                JOIN replay_policy_profile_detail d ON d.run_id=p.run_id
                WHERE p.run_id=?
                """, rs -> rs.next() ? new ReplayPolicyProfile(
                rs.getLong("run_id"), rs.getString("profile_hash"),
                rs.getTimestamp("effective_at").toInstant(),
                rs.getString("policy_fingerprint"), rs.getString("execution_profile"),
                rs.getBoolean("require_new_buy_transition"),
                rs.getBigDecimal("minimum_usdt_reserve"), rs.getBigDecimal("base_trade_amount_usdt"),
                rs.getInt("maximum_daily_new_positions"),
                rs.getString("exchange_constraint_fixture_hash"), rs.getBigDecimal("minimum_executable_notional_usdt"),
                rs.getBoolean("dynamic_profit_lock_enabled"),
                rs.getBigDecimal("profit_lock_activation_percent"),
                rs.getBigDecimal("profit_lock_initial_percent"),
                rs.getBigDecimal("profit_lock_trail_step_percent"),
                rs.getBigDecimal("fixed_notional_usdt")) : null, runId);
        if (profile == null) {
            throw new ReplayForbiddenDependencyException("FIX-156 missing frozen replay policy profile for run=" + runId);
        }
        return profile;
    }

    @Transactional
    public void insert(ReplayPolicyProfile profile) {
        // FIX-156: fixtures are prepared outside the Replay execution boundary.
        ReplayAccessPolicy.require("replay_policy_profile", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        int inserted = jdbc.update("""
                INSERT INTO replay_policy_profile
                (run_id, profile_hash, effective_at, dynamic_profit_lock_enabled,
                 profit_lock_activation_percent, profit_lock_initial_percent,
                 profit_lock_trail_step_percent, fixed_notional_usdt)
                VALUES (?,?,?,?,?,?,?,?)
                """, profile.runId(), profile.profileHash(), Timestamp.from(profile.effectiveAt()),
                profile.dynamicProfitLockEnabled(), profile.profitLockActivationPercent(),
                profile.profitLockInitialPercent(), profile.profitLockTrailStepPercent(),
                profile.fixedNotionalUsdt());
        if (inserted != 1) {
            throw new IllegalStateException("FIX-156 could not persist Replay policy profile");
        }
        ReplayAccessPolicy.require("replay_policy_profile_detail", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        int detailsInserted = jdbc.update("""
                INSERT INTO replay_policy_profile_detail
                (run_id, policy_fingerprint, execution_profile, require_new_buy_transition,
                 minimum_usdt_reserve, base_trade_amount_usdt, maximum_daily_new_positions,
                 exchange_constraint_fixture_hash, minimum_executable_notional_usdt)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, profile.runId(), profile.policyFingerprint(), profile.executionProfile(),
                profile.requireNewBuyTransition(), profile.minimumUsdtReserve(), profile.baseTradeAmountUsdt(),
                profile.maximumDailyNewPositions(), profile.exchangeConstraintFixtureHash(),
                profile.minimumExecutableNotionalUsdt());
        if (detailsInserted != 1) {
            throw new IllegalStateException("FIX-156 could not persist Replay policy profile details");
        }
    }
}
