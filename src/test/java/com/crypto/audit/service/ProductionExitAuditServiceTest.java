package com.crypto.audit.service;

import com.crypto.audit.domain.ProductionExitAudit;
import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.domain.PositionRecommendation;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductionExitAuditServiceTest {

    @Test
    void takeProfitKeepsWatchSignalAsContextInsteadOfCallingItSellTrigger() {
        ProductionExitAuditRepository auditRepository = mock(ProductionExitAuditRepository.class);
        WalletManagedPositionRepository managedRepository = mock(WalletManagedPositionRepository.class);
        PositionAnalysisRepository analysisRepository = mock(PositionAnalysisRepository.class);
        ProductionExitAuditService service = new ProductionExitAuditService(
                auditRepository, managedRepository, analysisRepository);

        TradeSignal entry = TradeSignal.builder().id(105616L).symbol("BTCUSDT").build();
        TradeSignal context = TradeSignal.builder()
                .id(105688L)
                .symbol("BTCUSDT")
                .decision(SignalDecision.WATCH)
                .originalDecision(SignalDecision.WATCH)
                .build();
        PaperPosition paper = PaperPosition.builder()
                .id(145L)
                .symbol("BTCUSDT")
                .entryPrice(new BigDecimal("73156.13"))
                .exitPrice(new BigDecimal("73393.85"))
                .stopLoss(new BigDecimal("72790.34935"))
                .takeProfit(new BigDecimal("73342.135913403430"))
                .signal(entry)
                .build();
        WalletManagedPosition managed = WalletManagedPosition.builder().id(212L).build();
        PositionAnalysis analysis = PositionAnalysis.builder()
                .id(1234L)
                .recommendation(PositionRecommendation.HOLD)
                .build();

        when(auditRepository.findTopByPaperPositionIdOrderByAuditedAtDesc(145L)).thenReturn(Optional.empty());
        when(managedRepository.findTopByEntrySignalIdOrderByOpenedAtDesc(105616L)).thenReturn(Optional.of(managed));
        when(analysisRepository.findTopByWalletPositionIdOrderByAnalyzedAtDesc(212L)).thenReturn(Optional.of(analysis));

        service.record(paper, context, "TAKE_PROFIT", "Price reached the configured take-profit target.");

        ArgumentCaptor<ProductionExitAudit> captor = ArgumentCaptor.forClass(ProductionExitAudit.class);
        verify(auditRepository).save(captor.capture());
        ProductionExitAudit saved = captor.getValue();
        assertThat(saved.getCloseTrigger()).isEqualTo("TAKE_PROFIT");
        assertThat(saved.getSourceSignalId()).isEqualTo(105688L);
        assertThat(saved.getSourceSignalDecision()).isEqualTo("WATCH");
        assertThat(saved.getPositionRecommendation()).isEqualTo("HOLD");
    }
}
