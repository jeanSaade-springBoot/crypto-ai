package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_signal")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TradeSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "interval_code", nullable = false, length = 10)
    private String interval;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SignalDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_decision", nullable = false, length = 30)
    private SignalDecision originalDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "confluence_status", nullable = false, length = 30)
    private ConfluenceStatus confluenceStatus;

    @Column(name = "confluence_entry_allowed", nullable = false)
    private boolean confluenceEntryAllowed;

    @Column(name = "confluence_higher_interval", length = 10)
    private String confluenceHigherInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "confluence_higher_decision", length = 30)
    private SignalDecision confluenceHigherDecision;

    @Column(name = "confluence_higher_trend_score")
    private Integer confluenceHigherTrendScore;

    @Column(name = "confluence_explanation", length = 1500)
    private String confluenceExplanation;

    @Column(name = "confluence_evaluated_at")
    private Instant confluenceEvaluatedAt;

    @Column(name = "confluence_higher_signal_generated_at")
    private Instant confluenceHigherSignalGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "btc_relationship_type", nullable = false, length = 30)
    private BtcRelationshipType btcRelationshipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "btc_context_status", nullable = false, length = 30)
    private BtcContextStatus btcContextStatus;

    @Column(name = "btc_context_entry_allowed", nullable = false)
    private boolean btcContextEntryAllowed;

    @Column(name = "btc_context_interval", length = 10)
    private String btcContextInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "btc_context_decision", length = 30)
    private SignalDecision btcContextDecision;

    @Column(name = "btc_context_trend_score")
    private Integer btcContextTrendScore;

    @Column(name = "btc_correlation", precision = 12, scale = 8)
    private BigDecimal btcCorrelation;

    @Column(name = "btc_beta", precision = 12, scale = 8)
    private BigDecimal btcBeta;

    @Column(name = "btc_relationship_sample_size", nullable = false)
    private int btcRelationshipSampleSize;

    @Column(name = "btc_influence_factor", precision = 8, scale = 6)
    private BigDecimal btcInfluenceFactor;

    @Column(name = "btc_relationship_stable", nullable = false)
    private boolean btcRelationshipStable;

    @Column(name = "btc_context_explanation", length = 1500)
    private String btcContextExplanation;

    @Column(name = "btc_context_evaluated_at")
    private Instant btcContextEvaluatedAt;

    @Column(name = "btc_signal_generated_at")
    private Instant btcSignalGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "derivatives_status", nullable = false, length = 40)
    private DerivativesPositioningStatus derivativesStatus;

    @Column(name = "derivatives_entry_allowed", nullable = false)
    private boolean derivativesEntryAllowed;

    @Column(name = "funding_rate", precision = 20, scale = 12)
    private BigDecimal fundingRate;

    @Column(name = "funding_percentile", precision = 8, scale = 2)
    private BigDecimal fundingPercentile;

    @Column(name = "open_interest", precision = 30, scale = 8)
    private BigDecimal openInterest;

    @Column(name = "open_interest_value", precision = 30, scale = 8)
    private BigDecimal openInterestValue;

    @Column(name = "open_interest_change_percent", precision = 20, scale = 8)
    private BigDecimal openInterestChangePercent;

    @Column(name = "derivatives_price_change_percent", precision = 20, scale = 8)
    private BigDecimal derivativesPriceChangePercent;

    @Column(name = "funding_sample_size", nullable = false)
    private int fundingSampleSize;

    @Column(name = "derivatives_period", length = 10)
    private String derivativesPeriod;

    @Column(name = "derivatives_confidence_adjustment", nullable = false)
    private int derivativesConfidenceAdjustment;

    @Column(name = "derivatives_explanation", length = 2000)
    private String derivativesExplanation;

    @Column(name = "derivatives_evaluated_at")
    private Instant derivativesEvaluatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquidity_status", nullable = false, length = 30)
    private LiquidityContextStatus liquidityStatus;

    @Column(name = "liquidity_entry_allowed", nullable = false)
    private boolean liquidityEntryAllowed;

    @Column(name = "order_book_imbalance", precision = 12, scale = 8)
    private BigDecimal orderBookImbalance;

    @Column(name = "order_book_bid_depth", precision = 30, scale = 8)
    private BigDecimal orderBookBidDepth;

    @Column(name = "order_book_ask_depth", precision = 30, scale = 8)
    private BigDecimal orderBookAskDepth;

    @Column(name = "order_book_spread_percent", precision = 20, scale = 10)
    private BigDecimal orderBookSpreadPercent;

    @Column(name = "nearest_bid_wall_price", precision = 30, scale = 12)
    private BigDecimal nearestBidWallPrice;

    @Column(name = "nearest_bid_wall_size", precision = 30, scale = 8)
    private BigDecimal nearestBidWallSize;

    @Column(name = "nearest_ask_wall_price", precision = 30, scale = 12)
    private BigDecimal nearestAskWallPrice;

    @Column(name = "nearest_ask_wall_size", precision = 30, scale = 8)
    private BigDecimal nearestAskWallSize;

    @Column(name = "order_book_target_blocked", nullable = false)
    private boolean orderBookTargetBlocked;

    @Column(name = "order_book_stop_exposed", nullable = false)
    private boolean orderBookStopExposed;

    @Column(name = "order_book_observations", nullable = false)
    private int orderBookObservations;

    @Column(name = "order_book_window_seconds", nullable = false)
    private long orderBookWindowSeconds;

    @Column(name = "order_book_wall_persistence_seconds", nullable = false)
    private long orderBookWallPersistenceSeconds;

    @Column(name = "order_book_influence_factor", precision = 8, scale = 6)
    private BigDecimal orderBookInfluenceFactor;

    @Column(name = "order_book_veto_allowed", nullable = false)
    private boolean orderBookVetoAllowed;

    @Column(name = "liquidity_explanation", length = 2000)
    private String liquidityExplanation;

    @Column(name = "liquidity_evaluated_at")
    private Instant liquidityEvaluatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_regime", nullable = false, length = 40)
    private MarketRegime marketRegime;

    @Column(name = "market_regime_confidence", nullable = false)
    private int marketRegimeConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_strategy", nullable = false, length = 40)
    private TradingStrategy selectedStrategy;

    @Column(name = "strategy_version", nullable = false, length = 30)
    private String strategyVersion;

    @Column(name = "strategy_entry_allowed", nullable = false)
    private boolean strategyEntryAllowed;

    @Column(name = "strategy_explanation", length = 1500)
    private String strategyExplanation;

    @Column(name = "strategy_breakdown", columnDefinition = "json")
    private String strategyBreakdown;

    @Column(name = "market_context_snapshot", columnDefinition = "json")
    private String marketContextSnapshot;

    @Column(name = "strategy_trend_maximum", nullable = false)
    private int strategyTrendMaximum;

    @Column(name = "strategy_volume_maximum", nullable = false)
    private int strategyVolumeMaximum;

    @Column(name = "strategy_momentum_maximum", nullable = false)
    private int strategyMomentumMaximum;

    @Column(name = "strategy_sentiment_maximum", nullable = false)
    private int strategySentimentMaximum;

    @Column(name = "strategy_fundamental_maximum", nullable = false)
    private int strategyFundamentalMaximum;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "confidence_score", nullable = false)
    private int confidenceScore;

    @Column(name = "final_entry_allowed", nullable = false)
    private boolean finalEntryAllowed;

    @Column(name = "decision_path", columnDefinition = "json")
    private String decisionPath;

    @Column(name = "final_decision_explanation", length = 2000)
    private String finalDecisionExplanation;
    @Column(name = "trend_score", nullable = false)
    private int trendScore;
    @Column(name = "volume_score", nullable = false)
    private int volumeScore;
    @Column(name = "momentum_score", nullable = false)
    private int momentumScore;
    @Column(name = "sentiment_score", nullable = false)
    private int sentimentScore;
    @Column(name = "fundamental_score", nullable = false)
    private int fundamentalScore;

    @Column(name = "ema_cross_score", nullable = false)
    private int emaCrossScore;
    @Column(name = "price_ema200_score", nullable = false)
    private int priceEma200Score;
    @Column(name = "ema_alignment_score", nullable = false)
    private int emaAlignmentScore;
    @Column(name = "sma20_score", nullable = false)
    private int sma20Score;

    @Column(name = "trend_direction_score", nullable = false)
    private int trendDirectionScore;
    @Column(name = "trend_structure_score", nullable = false)
    private int trendStructureScore;
    @Column(name = "trend_strength_score", nullable = false)
    private int trendStrengthScore;
    @Column(name = "trend_price_location_score", nullable = false)
    private int trendPriceLocationScore;
    @Column(name = "rsi_score", nullable = false)
    private int rsiScore;
    @Column(name = "macd_score", nullable = false)
    private int macdScore;
    @Column(name = "bollinger_score", nullable = false)
    private int bollingerScore;
    @Column(name = "relative_volume_score", nullable = false)
    private int relativeVolumeScore;
    @Column(name = "volume_sma20_score", nullable = false)
    private int volumeSma20Score;
    @Column(name = "raw_score", nullable = false)
    private int rawScore;
    @Column(name = "maximum_available_score", nullable = false)
    private int maximumAvailableScore;

    @Column(name = "sentiment_available", nullable = false)
    private boolean sentimentAvailable;

    @Column(name = "fundamental_available", nullable = false)
    private boolean fundamentalAvailable;

    @Column(name = "excluded_categories", columnDefinition = "json")
    private String excludedCategories;

    @Column(name = "sentiment_breakdown", columnDefinition = "json")
    private String sentimentBreakdown;

    @Column(name = "analysis_breakdown", columnDefinition = "json")
    private String analysisBreakdown;

    @Column(name = "latest_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal latestPrice;
    @Column(name = "stop_loss", precision = 30, scale = 12)
    private BigDecimal stopLoss;
    @Column(name = "take_profit", precision = 30, scale = 12)
    private BigDecimal takeProfit;

    @Column(name = "atr_at_signal", precision = 30, scale = 12)
    private BigDecimal atrAtSignal;
    @Column(name = "atr_percent", precision = 20, scale = 8)
    private BigDecimal atrPercent;
    @Column(name = "risk_reward_ratio", precision = 20, scale = 8)
    private BigDecimal riskRewardRatio;
    @Column(name = "candle_range_atr_multiple", precision = 20, scale = 8)
    private BigDecimal candleRangeAtrMultiple;
    @Column(name = "volatility_level", length = 20)
    private String volatilityLevel;
    @Column(name = "atr_overextended", nullable = false)
    private boolean atrOverextended;
    @Column(name = "atr_entry_type", length = 40)
    private String atrEntryType;
    @Column(name = "atr_recommended_position_percent", nullable = false)
    private int atrRecommendedPositionPercent;
    @Column(name = "atr_immediate_entry_allowed", nullable = false)
    private boolean atrImmediateEntryAllowed;
    @Column(name = "atr_retracement_entry_price", precision = 30, scale = 12)
    private BigDecimal atrRetracementEntryPrice;
    @Column(name = "atr_explanation", length = 1000)
    private String atrExplanation;

    @Column(length = 2000)
    private String explanation;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
