package com.crypto.debug.monitor.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceMoveMonitorSettingsRequest(
        boolean enabled,
        BigDecimal minimumMovePercent,
        int minimumDurationMinutes,
        BigDecimal retracementClosePercent,
        int cooldownMinutes,
        int retentionDays,
        List<String> symbols
) {}
