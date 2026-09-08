package com.crypto.service;

import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.repository.CandleRepository;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.market.service.MarketPriceEventService;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.crypto.infrastructure.transaction.KlineTransactionCoordinator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class BinanceKlineService {
    private final KlineTransactionCoordinator transactions;
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
            MarketPriceEventService marketPriceEventService,
            KlineTransactionCoordinator transactions
    ) {
        this.transactions = transactions;
        this.candleRepository = candleRepository;
        this.eventPublisher = eventPublisher;
        this.livePositionProtectionService = livePositionProtectionService;
        this.priceMoveMonitorService = priceMoveMonitorService;
        this.marketPriceEventService = marketPriceEventService;
    }

    // FIX-124: no suspended caller transaction may retain market-data locks.
    @Transactional(propagation = Propagation.NEVER)
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

        Instant observedAt = data.path("E").asLong(0L) > 0
                ? Instant.ofEpochMilli(data.path("E").asLong()) : Instant.now();
        boolean canonical = "1m".equals(intervalCode);
        // FIX-124: input commit -> protection commit/rollback -> observer -> close
        // dispatch. Exact candle identity and Binance observation time are retained.
        transactions.process(symbol, intervalCode, openTime, observedAt, livePrice,
                () -> {
                    candleRepository.upsert(
                            symbol, intervalCode, openTime, closeTime,
                            decimal(kline, "o"), decimal(kline, "h"), decimal(kline, "l"), livePrice,
                            decimal(kline, "v"), decimal(kline, "q"), kline.path("n").asLong(),
                            decimal(kline, "V"), decimal(kline, "Q"), closed);
                    if (canonical) marketPriceEventService.record(symbol, livePrice, observedAt);
                },
                canonical ? () -> livePositionProtectionService.onPrice(symbol, livePrice) : null,
                canonical ? () -> priceMoveMonitorService.onPrice(symbol, livePrice, Instant.now()) : null,
                closed ? () -> eventPublisher.publishEvent(new CandleClosedEvent(symbol, intervalCode, openTime)) : null);

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