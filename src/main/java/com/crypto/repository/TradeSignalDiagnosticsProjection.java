package com.crypto.repository;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;

/**
 * FIX-116A: narrow read model for Score Diagnostics.
 *
 * The TradeSignal entity contains large JSON/TEXT context fields that Score Diagnostics never reads.
 * Selecting only these scalar columns prevents a dashboard diagnostic request from materializing the
 * full Production signal payload. This is read-only and cannot change Production or Replay behavior.
 */
public interface TradeSignalDiagnosticsProjection {
    String getSymbol();
    String getInterval();
    int getTotalScore();
    int getRawScore();
    int getMaximumAvailableScore();
    int getTrendScore();
    int getVolumeScore();
    int getMomentumScore();
    int getSentimentScore();
    int getFundamentalScore();
    SignalDecision getOriginalDecision();
    SignalDecision getDecision();
    TradingStrategy getSelectedStrategy();
}
