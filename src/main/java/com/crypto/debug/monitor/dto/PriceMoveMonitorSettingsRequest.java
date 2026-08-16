package com.crypto.debug.monitor.dto;

import java.util.List;

/** Settings intentionally contain no old trigger/retracement/cooldown knobs. */
public record PriceMoveMonitorSettingsRequest(boolean enabled, List<String> symbols) {}
