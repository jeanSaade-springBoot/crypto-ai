package com.crypto.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.domain.Candle;
import com.crypto.repository.CandleRepository;

@Service
public class MarketDataService {
    @org.springframework.beans.factory.annotation.Value("#{'${shared-market.mode:OFF}' == 'LIVE'}")
    private boolean sharedMarketEnabled;


    private final BinanceMarketDataClient client;
    private final CandleRepository candleRepository;
    private final TransactionTemplate transaction;

    public MarketDataService(BinanceMarketDataClient client,CandleRepository candleRepository,PlatformTransactionManager manager){
        this.client=client;this.candleRepository=candleRepository;this.transaction=new TransactionTemplate(manager);
    }

    public int importCandles(String symbol, String interval, int limit) {
        if (sharedMarketEnabled) { throw new IllegalStateException("FIX-132: collector owns candle imports"); }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        // Do not trust a potentially skewed local clock to finalise Binance's
        // currently-open REST candle. Read it before klines: a later timestamp
        // could otherwise turn an in-flight response row into a false close.
        java.time.Instant exchangeNow=client.getServerTime();
        List<BinanceKline> klines = client.getKlines(normalizedSymbol, interval, limit);
        // No database transaction is open while Binance is contacted. The local
        // writes remain one transaction once the bounded network calls return.
        Integer upserted=transaction.execute(status->{
            int count=0;
            for (BinanceKline kline : klines) {
                // FIX-153: an in-progress REST candle is not a close event or historical evidence.
                if (kline.closeTime().isAfter(exchangeNow)) continue;
                count += candleRepository.upsert(
                        normalizedSymbol,interval,kline.openTime(),kline.closeTime(),
                        kline.openPrice(),kline.highPrice(),kline.lowPrice(),kline.closePrice(),
                        kline.volume(),kline.quoteAssetVolume(),kline.numberOfTrades(),
                        kline.takerBuyBaseVolume(),kline.takerBuyQuoteVolume(),true);
            }
            return count;
        });
        if(upserted==null)throw new IllegalStateException("FIX-153 local candle import transaction returned no result");
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
