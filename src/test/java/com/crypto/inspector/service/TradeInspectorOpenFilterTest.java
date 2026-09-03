package com.crypto.inspector.service;

import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.dto.TradeInspectorResponse;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletTradeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FIX-11I regression coverage for the read-only Trade Inspector OPEN/CLOSED selector.
 * Trading, wallet mutation and position-management behavior are deliberately outside this test.
 */
class TradeInspectorOpenFilterTest {

    @Test
    void openFilterReturnsOnlyPersistedOpenManagedPositionsAndKeepsExitEvidenceEmpty() {
        WalletTradeRepository walletTrades = mock(WalletTradeRepository.class);
        WalletManagedPositionRepository managedPositions = mock(WalletManagedPositionRepository.class);
        TradeSignalRepository signals = mock(TradeSignalRepository.class);
        TradeInspectorService service = new TradeInspectorService(
                walletTrades,
                mock(CandleRepository.class),
                mock(PaperPositionRepository.class),
                managedPositions,
                signals,
                mock(ExecutionOpportunityRepository.class),
                mock(ProductionExitAuditRepository.class),
                mock(PositionAnalysisRepository.class),
                mock(PositionManagementEventRepository.class), mock(org.springframework.jdbc.core.JdbcTemplate.class));

        WalletManagedPosition open = WalletManagedPosition.builder()
                .id(91L)
                .entrySignalId(501L)
                .symbol("EDUUSDT")
                .quantity(new BigDecimal("1250"))
                .averageEntryPriceUsdt(new BigDecimal("0.0480"))
                .entryDecision("BUY")
                .entryTotalScore(76)
                .entryConfidence(68)
                .stopLossUsdt(new BigDecimal("0.04776"))
                .takeProfitUsdt(new BigDecimal("0.04826"))
                .highestPriceUsdt(new BigDecimal("0.04840"))
                .entryStage("INITIAL")
                .allocatedPositionPercent(25)
                .entryQualityScore(50)
                .status("OPEN")
                .openedAt(Instant.parse("2026-08-29T17:00:00Z"))
                .updatedAt(Instant.parse("2026-08-29T17:01:00Z"))
                .build();

        when(managedPositions.findOpenPositionsForInspector(eq("EDUUSDT"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(open)));
        when(managedPositions.findDistinctOpenPositionSymbols()).thenReturn(List.of("EDUUSDT"));
        when(walletTrades.findDistinctClosedTradeSymbols()).thenReturn(List.of("BTCUSDT"));
        when(signals.findById(501L)).thenReturn(Optional.empty());
        when(walletTrades.findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(501L, "BUY", "EXECUTED"))
                .thenReturn(Optional.empty());

        TradeInspectorResponse response = service.inspect("EDUUSDT", "ALL", "OPEN", 0, 10);

        assertEquals(1, response.trades().size());
        var row = response.trades().get(0);
        assertEquals("OPEN", row.tradeState());
        assertEquals("EDUUSDT", row.symbol());
        assertEquals(new BigDecimal("0.0480"), row.entryPrice());
        assertNull(row.closedAt());
        assertNull(row.exitPrice());
        assertNull(row.realizedPnl());
        assertEquals("OPEN", row.closeReason());
        assertEquals(List.of("BTCUSDT", "EDUUSDT"), response.symbols());
        verify(walletTrades, never()).findClosedTradesForInspector(any(), any(Pageable.class));
    }
}
