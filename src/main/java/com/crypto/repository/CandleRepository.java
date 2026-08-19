package com.crypto.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crypto.domain.Candle;

public interface CandleRepository extends JpaRepository<Candle, Long> {

    Optional<Candle> findBySymbolAndIntervalCodeAndOpenTime(
            String symbol,
            String intervalCode,
            Instant openTime);

    List<Candle> findTop500BySymbolAndIntervalCodeOrderByOpenTimeDesc(
            String symbol,
            String intervalCode);

    @Modifying
    @Query(
        value = """
            INSERT INTO crypto_ai.candle (
                symbol,
                interval_code,
                open_time,
                close_time,
                open_price,
                high_price,
                low_price,
                close_price,
                volume,
                quote_asset_volume,
                number_of_trades,
                taker_buy_base_volume,
                taker_buy_quote_volume,
                closed,
                created_at,
                updated_at
            )
            VALUES (
                :symbol,
                :intervalCode,
                :openTime,
                :closeTime,
                :openPrice,
                :highPrice,
                :lowPrice,
                :closePrice,
                :volume,
                :quoteAssetVolume,
                :numberOfTrades,
                :takerBuyBaseVolume,
                :takerBuyQuoteVolume,
                :closed,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                close_time = VALUES(close_time),
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                quote_asset_volume = VALUES(quote_asset_volume),
                number_of_trades = VALUES(number_of_trades),
                taker_buy_base_volume = VALUES(taker_buy_base_volume),
                taker_buy_quote_volume = VALUES(taker_buy_quote_volume),
                closed = VALUES(closed),
                updated_at = CURRENT_TIMESTAMP(6)
            """,
        nativeQuery = true
    )
    int upsert(
            @Param("symbol")
            String symbol,

            @Param("intervalCode")
            String intervalCode,

            @Param("openTime")
            Instant openTime,

            @Param("closeTime")
            Instant closeTime,

            @Param("openPrice")
            BigDecimal openPrice,

            @Param("highPrice")
            BigDecimal highPrice,

            @Param("lowPrice")
            BigDecimal lowPrice,

            @Param("closePrice")
            BigDecimal closePrice,

            @Param("volume")
            BigDecimal volume,

            @Param("quoteAssetVolume")
            BigDecimal quoteAssetVolume,

            @Param("numberOfTrades")
            Long numberOfTrades,

            @Param("takerBuyBaseVolume")
            BigDecimal takerBuyBaseVolume,

            @Param("takerBuyQuoteVolume")
            BigDecimal takerBuyQuoteVolume,

            @Param("closed")
            boolean closed
    );
    
    
    List<Candle> findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc(
            String symbol,
            String intervalCode
    );
    
    @Query("""
            SELECT c
            FROM Candle c
            WHERE c.symbol = :symbol
              AND c.intervalCode = :intervalCode
              AND c.closed = true
            ORDER BY c.openTime DESC
            """)
    List<Candle> findClosedCandles(
            @Param("symbol") String symbol,
            @Param("intervalCode") String intervalCode,
            Pageable pageable
    );

    @Query("""
            SELECT c
            FROM Candle c
            WHERE c.symbol = :symbol
              AND c.intervalCode = :intervalCode
              AND c.closed = true
              AND c.openTime <= :maxOpenTime
            ORDER BY c.openTime DESC
            """)
    List<Candle> findClosedCandlesAtOrBefore(
            @Param("symbol") String symbol,
            @Param("intervalCode") String intervalCode,
            @Param("maxOpenTime") Instant maxOpenTime,
            Pageable pageable
    );
    @Query("""
            SELECT c
            FROM Candle c
            WHERE c.symbol = :symbol
              AND c.intervalCode = :intervalCode
              AND c.closed = true
              AND c.closeTime <= :maxCloseTime
            ORDER BY c.openTime DESC
            """)
    List<Candle> findClosedCandlesClosedAtOrBefore(
            @Param("symbol") String symbol,
            @Param("intervalCode") String intervalCode,
            @Param("maxCloseTime") Instant maxCloseTime,
            Pageable pageable
    );

    @Query("select distinct c.symbol from Candle c order by c.symbol")
    List<String> findDistinctSymbols();

    Optional<Candle> findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(
            String symbol,
            String intervalCode
    );

    List<Candle> findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
            String symbol, String intervalCode, Instant from, Instant to
    );

    /**
     * Trade Inspector full-history source. This remains read-only and returns only
     * real closed candles; the UI decides which visible window to focus initially.
     */
    List<Candle> findBySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeAsc(
            String symbol, String intervalCode
    );

    long countBySymbolAndIntervalCodeAndClosedTrue(
            String symbol,
            String intervalCode
    );

}

