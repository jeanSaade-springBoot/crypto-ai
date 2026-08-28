package com.crypto.debug.monitor.dto;

import java.util.List;

/** FIX-113: server-paged Catching Market aggregation response. */
public record CatchingMarketPageResponse(
        List<CatchingMarketSummaryView> rows,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {}
