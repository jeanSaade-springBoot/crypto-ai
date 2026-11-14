package com.crypto.inspector.service;

import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletTradeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// FIX-143: Verify the read-only filter routes to its own query and reports evidence honestly.
class TradeInspectorAllowedBuyFilterTest {
    @Test
    void allowedUnexecutedRowsHaveTheirOwnCategoryAndDoNotQueryPositionsOrCompletedTrades() {
        TradeSignalRepository signals = mock(TradeSignalRepository.class);
        WalletTradeRepository trades = mock(WalletTradeRepository.class);
        WalletManagedPositionRepository positions = mock(WalletManagedPositionRepository.class);
        TradeInspectorService service = new TradeInspectorService(trades, mock(CandleRepository.class),
                mock(PaperPositionRepository.class), positions, signals,
                mock(ExecutionOpportunityRepository.class), mock(ProductionExitAuditRepository.class),
                mock(PositionAnalysisRepository.class), mock(PositionManagementEventRepository.class),
                mock(JdbcTemplate.class));
        Instant candle = Instant.parse("2026-10-03T13:28:00Z");
        TradeSignal signal = TradeSignal.builder().id(77L).symbol("TAOUSDT").interval("1m")
                .decision(SignalDecision.BUY).originalDecision(SignalDecision.BUY)
                .finalEntryAllowed(true).candleOpenTime(candle).generatedAt(candle.plusSeconds(62)).build();
        when(signals.findAllowedUnexecutedBuys(eq(SignalDecision.BUY), eq(SignalDecision.STRONG_BUY),
                eq("TAOUSDT"), any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(signal));
        var rows = service.signalAnalysis("TAOUSDT", "1d", "ALLOWED_BUY", 999);
        assertEquals(1, rows.size());
        assertEquals("ALLOWED_BUY", rows.get(0).get("rowKind"));
        assertEquals("NOT_EXECUTED", rows.get(0).get("executionStatus"));
        assertEquals(true, rows.get(0).get("finalEntryAllowed"));
        assertEquals(candle, rows.get(0).get("eventTime"));
        assertTrue(rows.get(0).get("executionReason").toString().contains("cause not established"));
        verify(signals).findAllowedUnexecutedBuys(eq(SignalDecision.BUY), eq(SignalDecision.STRONG_BUY),
                eq("TAOUSDT"), any(Instant.class), any(Instant.class),
                argThat(page -> page.getPageSize() == 250 && page.getPageNumber() == 0));
        verifyNoMoreInteractions(signals);
        verifyNoInteractions(trades, positions);
    }
}
