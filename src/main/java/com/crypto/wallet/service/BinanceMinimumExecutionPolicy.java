package com.crypto.wallet.service;

import com.crypto.client.binance.BinanceMarketDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

/**
 * FIX-11T minimum-executable-order guard shared by Production Paper and Replay.
 *
 * The project intentionally models Paper/Replay as real Binance trading with only the
 * final connector missing.  Therefore a Near-TP partial harvest that Binance would reject
 * for being below the symbol's minimum notional is not simulated.  No alternative exit
 * size is invented: the position simply continues through its existing management logic.
 *
 * This class deliberately checks only the agreed minimum executable notional boundary.
 * It does not add a new Near-TP-specific step-size/precision model.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceMinimumExecutionPolicy {
    private static final MathContext MC = MathContext.DECIMAL64;

    private final BinanceMarketDataClient binanceMarketDataClient;
    public Evaluation evaluate(String symbol, BigDecimal quantity, BigDecimal price) {
        if (symbol == null || symbol.isBlank() || quantity == null || quantity.signum() <= 0
                || price == null || price.signum() <= 0) {
            return new Evaluation(false, "INVALID_ORDER_INPUT", BigDecimal.ZERO, null);
        }
        String pair = symbol.trim().toUpperCase(Locale.ROOT);
        BigDecimal notional = quantity.multiply(price, MC);
        BigDecimal minimum;
        try {
            minimum = binanceMarketDataClient.getMinimumExecutableNotional(pair);
        } catch (RuntimeException ex) {
            // Fail safe. Missing exchange constraints must never be converted into permission
            // to create an order that real Binance might reject.
            log.warn("FIX-11T Binance minimum executable amount unavailable: symbol={}, requestedNotional={}, error={}",
                    pair, notional, ex.getMessage());
            return new Evaluation(false, "BINANCE_MINIMUM_UNAVAILABLE", notional, null);
        }
        if (notional.compareTo(minimum) < 0) {
            return new Evaluation(false, "BELOW_BINANCE_MINIMUM", notional, minimum);
        }
        return new Evaluation(true, "EXECUTABLE", notional, minimum);
    }

    public record Evaluation(boolean executable, String code, BigDecimal requestedNotional, BigDecimal minimumNotional) {
    }
}
