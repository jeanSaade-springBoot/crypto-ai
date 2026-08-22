package com.crypto.service;

import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.repository.CandleRepository;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.market.service.MarketPriceEventService;
import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class BinanceKlineService {
    private static final Logger log =
            LoggerFactory.getLogger(BinanceKlineService.class);
    private final CandleRepository candleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LivePositionProtectionService livePositionProtectionService;
    private final PriceMoveMonitorService priceMoveMonitorService;
    private final MarketPriceEventService marketPriceEventService;

    public BinanceKlineService(
            CandleRepository candleRepository,
            ApplicationEventPublisher eventPublisher,
            LivePositionProtectionService livePositionProtectionService,
            PriceMoveMonitorService priceMoveMonitorService,
            MarketPriceEventService marketPriceEventService
    ) {
        this.candleRepository = candleRepository;
        this.eventPublisher = eventPublisher;
        this.livePositionProtectionService = livePositionProtectionService;
        this.priceMoveMonitorService = priceMoveMonitorService;
        this.marketPriceEventService = marketPriceEventService;
    }

    @Transactional
    public boolean processKline(JsonNode root) {

        JsonNode data = root.has("data")
                ? root.path("data")
                : root;

        JsonNode kline = data.path("k");

        if (kline.isMissingNode() || kline.isNull()) {
            throw new IllegalArgumentException(
                    "Binance message does not contain kline data"
            );
        }

        String symbol = kline.path("s")
                .asText()
                .trim()
                .toUpperCase(Locale.ROOT);

        String intervalCode = kline.path("i")
                .asText()
                .trim();

        Instant openTime = Instant.ofEpochMilli(
                kline.path("t").asLong()
        );

        Instant closeTime = Instant.ofEpochMilli(
                kline.path("T").asLong()
        );

        boolean closed = kline.path("x")
                .asBoolean(false);
        BigDecimal livePrice = decimal(kline, "c");

        candleRepository.upsert(
                symbol,
                intervalCode,
                openTime,
                closeTime,
                decimal(kline, "o"),
                decimal(kline, "h"),
                decimal(kline, "l"),
                livePrice,
                decimal(kline, "v"),
                decimal(kline, "q"),
                kline.path("n").asLong(),
                decimal(kline, "V"),
                decimal(kline, "Q"),
                closed
        );

        // Mechanical position protection must react to live price updates, not wait
        // for a candle-close analysis signal. Use the 1m stream as the canonical live feed
        // to avoid duplicate checks from 5m/1h subscriptions.
        if ("1m".equals(intervalCode)) {
            // FIX-052: persist the exact canonical live-price observation BEFORE
            // Production position protection consumes it. Replay later uses this
            // same UTC-timestamped event stream and ordering instead of candle-close
            // approximations for TP/SL/profit-lock decisions.
            Instant observedAt = data.path("E").asLong(0L) > 0
                    ? Instant.ofEpochMilli(data.path("E").asLong())
                    : Instant.now();
            try {
                marketPriceEventService.record(symbol, livePrice, observedAt);
            } catch (RuntimeException ex) {
                log.error("Unable to persist live price event for exact Replay parity: symbol={}, price={}, observedAt={}, error={}",
                        symbol, livePrice, observedAt, ex.getMessage(), ex);
            }
            try {
                livePositionProtectionService.onPrice(symbol, livePrice);
            } catch (RuntimeException ex) {
                log.error("Live position protection failed: symbol={}, price={}, error={}",
                        symbol, livePrice, ex.getMessage(), ex);
            }
            try {
                // DEBUG-ONLY one-way observer. It records price moves and cannot influence trading decisions.
                priceMoveMonitorService.onPrice(symbol, livePrice, Instant.now());
            } catch (RuntimeException ex) {
                log.warn("Debug price move monitor failed: symbol={}, price={}, error={}",
                        symbol, livePrice, ex.getMessage());
            }
        }

        
        log.info(
        	    "before closed event triggered fro symbol ={}, interval={}",
        	    symbol,
        	    intervalCode
        	);
        
        if (closed) {
        	
        	 log.info(
             	    "closed event triggered fro symbol ={}, interval={}",
             	    symbol,
             	    intervalCode
             	);
            eventPublisher.publishEvent(
                    new CandleClosedEvent(
                            symbol,
                            intervalCode,
                            openTime
                    )
            );
        }

        return closed;
    }

    private BigDecimal decimal(
            JsonNode node,
            String field
    ) {
        String value = node.path(field).asText();

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value);
    }
}