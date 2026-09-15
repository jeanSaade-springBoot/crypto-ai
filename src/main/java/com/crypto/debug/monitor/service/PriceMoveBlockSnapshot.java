package com.crypto.debug.monitor.service;

import java.math.BigDecimal;

/** FIX-130: immutable completed-block facts only. String times are ISO UTC;
 * neither the tracker nor a managed entity escapes onto the background thread. */
public record PriceMoveBlockSnapshot(String symbol, String blockStart, Move up, Move down) {
    public record Move(String start, String end, BigDecimal startPrice, BigDecimal endPrice,
                       BigDecimal change, String level, String window) {}
}
