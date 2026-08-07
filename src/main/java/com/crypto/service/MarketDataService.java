package com.crypto.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.domain.Candle;
import com.crypto.repository.CandleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final BinanceMarketDataClient client;
    private final CandleRepository candleRepository;

    @Transactional
    public int importCandles(String symbol, String interval, int limit) {
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        List<BinanceKline> klines = client.getKlines(normalizedSymbol, interval, limit);
        int upserted = 0;

        for (BinanceKline kline : klines) {
            upserted += candleRepository.upsert(
                    normalizedSymbol,
                    interval,
                    kline.openTime(),
                    kline.closeTime(),
                    kline.openPrice(),
                    kline.highPrice(),
                    kline.lowPrice(),
                    kline.closePrice(),
                    kline.volume(),
                    kline.quoteAssetVolume(),
                    kline.numberOfTrades(),
                    kline.takerBuyBaseVolume(),
                    kline.takerBuyQuoteVolume(),
                    true
            );
        }
        return upserted;
    }

    @Transactional(readOnly = true)
    public List<Candle> latestCandles(String symbol, String interval) {
        return candleRepository
                .findTop500BySymbolAndIntervalCodeOrderByOpenTimeDesc(
                        symbol.trim().toUpperCase(Locale.ROOT), interval)
                .stream()
                .sorted(Comparator.comparing(Candle::getOpenTime))
                .toList();
    }

    private Candle toEntity(String symbol, String interval, BinanceKline kline) {
        return Candle.builder()
                .symbol(symbol)
                .intervalCode(interval)
                .openTime(kline.openTime())
                .closeTime(kline.closeTime())
                .openPrice(kline.openPrice())
                .highPrice(kline.highPrice())
                .lowPrice(kline.lowPrice())
                .closePrice(kline.closePrice())
                .volume(kline.volume())
                .quoteAssetVolume(kline.quoteAssetVolume())
                .numberOfTrades(kline.numberOfTrades())
                .takerBuyBaseVolume(kline.takerBuyBaseVolume())
                .takerBuyQuoteVolume(kline.takerBuyQuoteVolume())
                .closed(true)
                .build();
    }
}
