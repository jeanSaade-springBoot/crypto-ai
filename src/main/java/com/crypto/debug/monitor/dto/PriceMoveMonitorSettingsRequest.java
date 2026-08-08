package com.crypto.debug.monitor.dto;

import java.math.BigDecimal;

public record PriceMoveMonitorSettingsRequest(
        boolean enabled,
        BigDecimal minimumMovePercent,
        int minimumDurationMinutes,
        BigDecimal retracementClosePercent,
        int cooldownMinutes,
        int retentionDays
) {}
