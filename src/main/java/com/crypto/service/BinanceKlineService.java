package com.crypto.service;

import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.CandleRepository;
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

    public BinanceKlineService(
            CandleRepository candleRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.candleRepository = candleRepository;
        this.eventPublisher = eventPublisher;
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

        candleRepository.upsert(
                symbol,
                intervalCode,
                openTime,
                closeTime,
                decimal(kline, "o"),
                decimal(kline, "h"),
                decimal(kline, "l"),
                decimal(kline, "c"),
                decimal(kline, "v"),
                decimal(kline, "q"),
                kline.path("n").asLong(),
                decimal(kline, "V"),
                decimal(kline, "Q"),
                closed
        );

        
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