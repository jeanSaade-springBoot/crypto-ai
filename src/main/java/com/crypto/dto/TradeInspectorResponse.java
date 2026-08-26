package com.crypto.dto;

import java.util.List;

/**
 * FIX-106: Trade Inspector response now carries database-pagination metadata.
 * Existing summary/trade/symbol fields are preserved so the UI contract remains additive.
 */
public record TradeInspectorResponse(
        TradeInspectorSummary summary,
        List<TradeInspectorTradeView> trades,
        List<String> symbols,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {}
