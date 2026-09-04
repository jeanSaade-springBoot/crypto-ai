package com.crypto.wallet.domain;

import com.crypto.position.service.NearTpState;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_managed_position")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletManagedPosition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entry_signal_id")
    private Long entrySignalId;
    @Column(nullable = false, length = 30)
    private String symbol;
    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;
    @Column(name = "average_entry_price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal averageEntryPriceUsdt;
    @Column(name = "total_cost_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal totalCostUsdt;
    @Column(name = "entry_confidence")
    private Integer entryConfidence;
    @Column(name = "entry_total_score")
    private Integer entryTotalScore;
    @Column(name = "entry_trend_score")
    private Integer entryTrendScore;
    @Column(name = "entry_structure_score")
    private Integer entryStructureScore;
    @Column(name = "entry_momentum_score")
    private Integer entryMomentumScore;
    @Column(name = "entry_volume_score")
    private Integer entryVolumeScore;
    @Column(name = "entry_sentiment_score")
    private Integer entrySentimentScore;
    @Column(name = "entry_fundamental_score")
    private Integer entryFundamentalScore;
    @Column(name = "entry_decision", length = 30)
    private String entryDecision;
    @Column(name = "entry_decision_path_json", columnDefinition = "json")
    private String entryDecisionPathJson;
    @Column(name = "entry_analysis_snapshot_json", columnDefinition = "json")
    private String entryAnalysisSnapshotJson;
    @Column(name = "stop_loss_usdt", precision = 30, scale = 12)
    private BigDecimal stopLossUsdt;
    @Column(name = "take_profit_usdt", precision = 30, scale = 12)
    private BigDecimal takeProfitUsdt;
    @Column(name = "highest_price_usdt", precision = 30, scale = 12)
    private BigDecimal highestPriceUsdt;
    @Column(name = "profit_lock_active", nullable = false)
    private boolean profitLockActive;
    @Column(name = "profit_lock_price_usdt", precision = 30, scale = 12)
    private BigDecimal profitLockPriceUsdt;
    @Column(name = "profit_lock_progress_percent", precision = 12, scale = 6)
    private BigDecimal profitLockProgressPercent;
    @Column(name = "profit_lock_activated_at")
    private Instant profitLockActivatedAt;
    @Column(name = "entry_stage", nullable = false, length = 30)
    private String entryStage;
    @Column(name = "allocated_position_percent", nullable = false)
    private int allocatedPositionPercent;
    @Column(name = "entry_quality_score", nullable = false)
    private int entryQualityScore;
    @Column(name = "last_scale_in_at")
    private Instant lastScaleInAt;

    // FIX-11T: persisted Near-TP Failure Protection state. These fields are position-scoped
    // so a restart cannot forget that a rejection was already armed/confirmed or count the
    // same 1m signal multiple times toward bearish persistence.
    @Enumerated(EnumType.STRING)
    @Column(name = "near_tp_state", nullable = false, length = 40)
    @Builder.Default
    private NearTpState nearTpState = NearTpState.INACTIVE;
    @Column(name = "near_tp_best_price", precision = 30, scale = 12)
    private BigDecimal nearTpBestPrice;
    @Column(name = "near_tp_bearish_streak", nullable = false)
    private int nearTpBearishStreak;
    @Column(name = "near_tp_last_1m_signal_id")
    private Long nearTpLastOneMinuteSignalId;
    @Column(name = "near_tp_harvest_used", nullable = false)
    private boolean nearTpHarvestUsed;
    @Column(name = "near_tp_harvested_quantity", precision = 30, scale = 12)
    private BigDecimal nearTpHarvestedQuantity;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
