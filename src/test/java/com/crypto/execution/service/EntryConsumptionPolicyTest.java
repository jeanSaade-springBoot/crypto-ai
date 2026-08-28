package com.crypto.execution.service;

import com.crypto.execution.domain.EntryConsumptionState;
import com.crypto.wallet.domain.WalletTrade;
import com.crypto.wallet.repository.WalletTradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryConsumptionPolicyTest {
    @Mock WalletTradeRepository walletTradeRepository;
    @Mock ExecutionReplayScope replayScope;

    @Test
    void blockedFirstOrProgressiveEntryIsNotConsumedWithoutExecutedBuyForThatSignal() {
        long previousSignalId = 101L;
        when(replayScope.active()).thenReturn(false);
        when(walletTradeRepository.findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                previousSignalId, "BUY", "EXECUTED")).thenReturn(Optional.empty());

        EntryConsumptionPolicy policy = new EntryConsumptionPolicy(walletTradeRepository, replayScope);
        assertThat(policy.resolve(previousSignalId)).isEqualTo(EntryConsumptionState.NOT_CONSUMED);
    }

    @Test
    void executedFirstOrProgressiveEntryIsConsumedOnlyForItsExactSignal() {
        long executedSignalId = 202L;
        long blockedSignalId = 203L;
        when(replayScope.active()).thenReturn(false);
        when(walletTradeRepository.findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                executedSignalId, "BUY", "EXECUTED")).thenReturn(Optional.of(WalletTrade.builder().build()));
        when(walletTradeRepository.findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                blockedSignalId, "BUY", "EXECUTED")).thenReturn(Optional.empty());

        EntryConsumptionPolicy policy = new EntryConsumptionPolicy(walletTradeRepository, replayScope);
        assertThat(policy.resolve(executedSignalId)).isEqualTo(EntryConsumptionState.CONSUMED);
        assertThat(policy.resolve(blockedSignalId)).isEqualTo(EntryConsumptionState.NOT_CONSUMED);
    }

    @Test
    void replayUsesOnlySignalSpecificShadowConsumption() {
        long executedSignalId = 301L;
        long blockedSignalId = 302L;
        when(replayScope.active()).thenReturn(true);
        when(replayScope.entryConsumed(executedSignalId)).thenReturn(true);
        when(replayScope.entryConsumed(blockedSignalId)).thenReturn(false);

        EntryConsumptionPolicy policy = new EntryConsumptionPolicy(walletTradeRepository, replayScope);
        assertThat(policy.resolve(executedSignalId)).isEqualTo(EntryConsumptionState.CONSUMED);
        assertThat(policy.resolve(blockedSignalId)).isEqualTo(EntryConsumptionState.NOT_CONSUMED);
    }
}
