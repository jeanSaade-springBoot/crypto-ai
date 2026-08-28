package com.crypto.execution.service;

import com.crypto.execution.domain.EntryConsumptionState;
import com.crypto.wallet.repository.WalletTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * FIX-112A: consumption belongs to the specific bullish signal, not to the
 * symbol's general position state. A pre-existing position can coexist with a
 * blocked progressive-add signal, so OPEN-position state is not proof that the
 * immediately previous bullish signal actually executed.
 */
@Service
@RequiredArgsConstructor
public class EntryConsumptionPolicy {
    private final WalletTradeRepository walletTradeRepository;
    private final ExecutionReplayScope replayScope;

    public EntryConsumptionState resolve(Long previousSignalId) {
        if (previousSignalId == null) return EntryConsumptionState.NOT_CONSUMED;
        if (replayScope.active()) {
            return replayScope.entryConsumed(previousSignalId)
                    ? EntryConsumptionState.CONSUMED
                    : EntryConsumptionState.NOT_CONSUMED;
        }
        // Production authority is the immutable executed BUY row keyed to the
        // exact triggering signal. This works for both first entries and adds.
        boolean executed = walletTradeRepository
                .findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                        previousSignalId, "BUY", "EXECUTED")
                .isPresent();
        return executed ? EntryConsumptionState.CONSUMED : EntryConsumptionState.NOT_CONSUMED;
    }
}
