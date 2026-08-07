package com.crypto.dto;

import java.util.List;

public record TradeInspectorResponse(
        TradeInspectorSummary summary,
        List<TradeInspectorTradeView> trades,
        List<String> symbols
) {}
