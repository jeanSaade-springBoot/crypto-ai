package com.crypto.whale.service;

import com.crypto.domain.Candle;
import com.crypto.repository.CandleRepository;
import com.crypto.whale.config.WhaleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WhalePriceService {
    private final CandleRepository candleRepository;
    private final WhaleProperties properties;

    @Transactional(readOnly = true)
    public BigDecimal latestPrice(String symbol) {
        return candleRepository.findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(
                        symbol, properties.evaluation().priceInterval())
                .map(Candle::getClosePrice)
                .orElseThrow(() -> new IllegalStateException("No closed candle available for " + symbol));
    }
}
