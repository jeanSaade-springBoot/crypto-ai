package com.crypto.audit.service;

import com.crypto.audit.domain.ProductionExitAudit;
import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.TradeSignal;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * FIX-028: records the actual production exit cause without changing execution.
 *
 * Example that motivated the audit: BTC hit TAKE_PROFIT while the latest 1m signal
 * was WATCH. The legacy wallet ledger called that SIGNAL_SELL because it reused the
 * current signal as an execution carrier. This service preserves both facts:
 * closeTrigger=TAKE_PROFIT and sourceSignalDecision=WATCH.
 */
@Service
@RequiredArgsConstructor
public class ProductionExitAuditService {
    private final ProductionExitAuditRepository auditRepository;
    private final WalletManagedPositionRepository managedPositionRepository;
    private final PositionAnalysisRepository positionAnalysisRepository;

    @Transactional
    public void record(PaperPosition paper, TradeSignal sourceSignal, String closeTrigger, String explanation) {
        if (paper == null || paper.getId() == null || paper.getExitPrice() == null || closeTrigger == null) return;
        if (auditRepository.findTopByPaperPositionIdOrderByAuditedAtDesc(paper.getId()).isPresent()) return;

        WalletManagedPosition managed = paper.getSignal() == null ? null : managedPositionRepository
                .findTopByEntrySignalIdOrderByOpenedAtDesc(paper.getSignal().getId()).orElse(null);
        PositionAnalysis analysis = managed == null || managed.getId() == null ? null : positionAnalysisRepository
                .findTopByWalletPositionIdOrderByAnalyzedAtDesc(managed.getId()).orElse(null);

        auditRepository.save(ProductionExitAudit.builder()
                .paperPositionId(paper.getId())
                .walletPositionId(managed == null ? null : managed.getId())
                .symbol(paper.getSymbol())
                .closeTrigger(closeTrigger)
                .sourceSignalId(sourceSignal == null ? null : sourceSignal.getId())
                .sourceSignalDecision(sourceSignal == null || sourceSignal.getDecision() == null ? null : sourceSignal.getDecision().name())
                .sourceSignalOriginalDecision(sourceSignal == null || sourceSignal.getOriginalDecision() == null ? null : sourceSignal.getOriginalDecision().name())
                .positionAnalysisId(analysis == null ? null : analysis.getId())
                .positionRecommendation(analysis == null || analysis.getRecommendation() == null ? null : analysis.getRecommendation().name())
                .entryPriceUsdt(paper.getEntryPrice())
                .exitPriceUsdt(paper.getExitPrice())
                .stopLossUsdt(paper.getStopLoss())
                .takeProfitUsdt(paper.getTakeProfit())
                .closeExplanation(explanation)
                .auditedAt(Instant.now())
                .build());
    }
}
