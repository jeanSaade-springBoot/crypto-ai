package com.crypto.indicator.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.indicator.calculator.AtrCalculator;
import com.crypto.indicator.calculator.BollingerBandsCalculator;
import com.crypto.indicator.calculator.EmaCalculator;
import com.crypto.indicator.calculator.MacdCalculator;
import com.crypto.indicator.calculator.RelativeVolumeCalculator;
import com.crypto.indicator.calculator.RsiCalculator;
import com.crypto.indicator.calculator.SmaCalculator;
import com.crypto.indicator.event.CandleClosedEventListener;
import com.crypto.indicator.model.BollingerBandsResult;
import com.crypto.indicator.model.MacdResult;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TechnicalIndicatorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TechnicalIndicatorService {
    private static final Logger log =
            LoggerFactory.getLogger(TechnicalIndicatorService.class);
    private static final int HISTORY_LIMIT = 300;
    private static final int MINIMUM_CANDLES = 210;

    private final CandleRepository candleRepository;
    private final TechnicalIndicatorRepository indicatorRepository;

    private final SmaCalculator smaCalculator;
    private final EmaCalculator emaCalculator;
    private final RsiCalculator rsiCalculator;
    private final BollingerBandsCalculator bollingerBandsCalculator;
    private final AtrCalculator atrCalculator;
    private final MacdCalculator macdCalculator;
    private final RelativeVolumeCalculator relativeVolumeCalculator;

    public TechnicalIndicatorService(
            CandleRepository candleRepository,
            TechnicalIndicatorRepository indicatorRepository,
            SmaCalculator smaCalculator,
            EmaCalculator emaCalculator,
            RsiCalculator rsiCalculator,
            BollingerBandsCalculator bollingerBandsCalculator,
            AtrCalculator atrCalculator,
            MacdCalculator macdCalculator,
            RelativeVolumeCalculator relativeVolumeCalculator
    ) {
        this.candleRepository = candleRepository;
        this.indicatorRepository = indicatorRepository;
        this.smaCalculator = smaCalculator;
        this.emaCalculator = emaCalculator;
        this.rsiCalculator = rsiCalculator;
        this.bollingerBandsCalculator =
                bollingerBandsCalculator;
        this.atrCalculator = atrCalculator;
        this.macdCalculator = macdCalculator;
        this.relativeVolumeCalculator =
                relativeVolumeCalculator;
    }

    /**
     * Loads the latest closed-candle history, calculates all indicators and
     * persists the resulting technical_indicator row.
     *
     * @param expectedCandleOpenTime expected latest candle open time for the
     *                               event-driven flow; pass {@code null} for
     *                               manual or scheduled latest-candle analysis
     */
    @Transactional
    public Optional<TechnicalIndicator> calculateAndPersist(
            String symbol,
            String intervalCode,
            Instant expectedCandleOpenTime
    ) {
        Optional<IndicatorSnapshot> snapshotResult =
                calculateSnapshotFromDatabase(
                        symbol,
                        intervalCode
                );

        if (snapshotResult.isEmpty()) {
            return Optional.empty();
        }

        IndicatorSnapshot snapshot = snapshotResult.get();

        if (expectedCandleOpenTime != null
                && !snapshot.candleOpenTime()
                        .equals(expectedCandleOpenTime)) {
            return Optional.empty();
        }

        return Optional.of(
                saveTechnicalIndicator(snapshot)
        );
    }

    @Transactional(readOnly = true)
    public Optional<TechnicalIndicator> getLatest(
            String symbol,
            String intervalCode
    ) {
        return indicatorRepository
                .findTopBySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
                        normaliseSymbol(symbol),
                        normaliseInterval(intervalCode)
                );
    }

    @Transactional(readOnly = true)
    public List<TechnicalIndicator> getHistory(
            String symbol,
            String intervalCode
    ) {
        return indicatorRepository
                .findTop100BySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
                        normaliseSymbol(symbol),
                        normaliseInterval(intervalCode)
                );
    }

    private Optional<IndicatorSnapshot>
    calculateSnapshotFromDatabase(
            String symbol,
            String intervalCode
    ) {
        String normalisedSymbol =
                normaliseSymbol(symbol);

        String normalisedInterval =
                normaliseInterval(intervalCode);

        List<Candle> candles =
                new ArrayList<>(
                        candleRepository.findClosedCandles(
                                normalisedSymbol,
                                normalisedInterval,
                                PageRequest.of(
                                        0,
                                        HISTORY_LIMIT
                                )
                        )
                );

        if (candles.size() < MINIMUM_CANDLES) {
            log.warn(
                    "Indicator calculation skipped: symbol={}, interval={}, closedCandles={}, required={}",
                    normalisedSymbol,
                    normalisedInterval,
                    candles.size(),
                    MINIMUM_CANDLES
            );
            return Optional.empty();
        }

        Collections.reverse(candles);

        log.info(
        	    "Calculating indicators before snapshot for symbol={}, interval={}",
        	    symbol,
        	    intervalCode
        	);
        
        IndicatorSnapshot snapshot =
                calculateSnapshot(
                        normalisedSymbol,
                        normalisedInterval,
                        candles
                );
        log.info(
        	    "Calculating indicators after snapshot for symbol={}, interval={}",
        	    symbol,
        	    intervalCode
        	);
        return Optional.of(snapshot);
    }

    private IndicatorSnapshot calculateSnapshot(
            String symbol,
            String intervalCode,
            List<Candle> candles
    ) {
        validateCandles(candles);

        Candle latest =
                candles.get(candles.size() - 1);

        List<BigDecimal> closePrices =
                candles.stream()
                        .map(Candle::getClosePrice)
                        .toList();

        List<BigDecimal> volumes =
                candles.stream()
                        .map(Candle::getVolume)
                        .toList();

        BigDecimal sma20 =
                smaCalculator.calculate(
                        closePrices,
                        20
                );

        BigDecimal ema20 =
                emaCalculator.calculate(
                        closePrices,
                        20
                );

        BigDecimal ema50 =
                emaCalculator.calculate(
                        closePrices,
                        50
                );

        BigDecimal ema200 =
                emaCalculator.calculate(
                        closePrices,
                        200
                );

        BigDecimal rsi14 =
                rsiCalculator.calculate(
                        closePrices,
                        14
                );

        BollingerBandsResult bands =
                bollingerBandsCalculator.calculate(
                        closePrices,
                        20,
                        BigDecimal.valueOf(2)
                );

        BigDecimal atr14 =
                atrCalculator.calculate(
                        candles,
                        14
                );

        // Use the previous 20 closed candles as the volume baseline.
        // The latest candle is deliberately excluded so RVOL is not diluted
        // by including the value being compared in its own average.
        BigDecimal volumeSma20 =
                smaCalculator.calculate(
                        volumes.subList(0, volumes.size() - 1),
                        20
                );

        BigDecimal relativeVolume =
                relativeVolumeCalculator.calculate(
                        candles,
                        20
                );

        MacdResult macd =
                macdCalculator.calculate(
                        closePrices
                );

        return new IndicatorSnapshot(
                normaliseSymbol(symbol),
                normaliseInterval(intervalCode),
                latest.getOpenTime(),
                latest.getClosePrice(),

                sma20,

                ema20,
                ema50,
                ema200,

                rsi14,

                macd.macd(),
                macd.signal(),
                macd.histogram(),

                bands.middle(),
                bands.upper(),
                bands.lower(),
                bands.bandwidth(),

                atr14,

                latest.getVolume(),
                volumeSma20,
                relativeVolume
        );
    }

    private TechnicalIndicator saveTechnicalIndicator(
            IndicatorSnapshot snapshot
    ) {
        TechnicalIndicator indicator =
                indicatorRepository
                        .findBySymbolAndIntervalCodeAndCandleOpenTime(
                                snapshot.symbol(),
                                snapshot.intervalCode(),
                                snapshot.candleOpenTime()
                        )
                        .orElseGet(
                                TechnicalIndicator::new
                        );

        indicator.setSymbol(snapshot.symbol());
        indicator.setIntervalCode(
                snapshot.intervalCode()
        );
        indicator.setCandleOpenTime(
                snapshot.candleOpenTime()
        );
        indicator.setClosePrice(
                snapshot.latestPrice()
        );

        indicator.setSma20(snapshot.sma20());
        indicator.setEma20(snapshot.ema20());
        indicator.setEma50(snapshot.ema50());
        indicator.setEma200(snapshot.ema200());
        indicator.setRsi14(snapshot.rsi14());

        indicator.setMacd(snapshot.macd());
        indicator.setMacdSignal(snapshot.macdSignal());
        indicator.setMacdHistogram(snapshot.macdHistogram());

        indicator.setBollingerMiddle(
                snapshot.bollingerMiddle()
        );
        indicator.setBollingerUpper(
                snapshot.bollingerUpper()
        );
        indicator.setBollingerLower(
                snapshot.bollingerLower()
        );
        indicator.setBollingerBandwidth(
                snapshot.bollingerBandwidth()
        );

        indicator.setAtr14(snapshot.atr14());
        indicator.setLatestVolume(snapshot.latestVolume());
        indicator.setVolumeSma20(
                snapshot.volumeSma20()
        );
        indicator.setRelativeVolume(
                snapshot.relativeVolume()
        );

        return indicatorRepository.save(indicator);
    }

    private void validateCandles(
            List<Candle> candles
    ) {
        if (candles == null
                || candles.size() < MINIMUM_CANDLES) {
            throw new IllegalArgumentException(
                    "At least "
                            + MINIMUM_CANDLES
                            + " candles are required"
            );
        }
    }

    private String normaliseSymbol(
            String symbol
    ) {
    	log.info(
    		    " normaliseSymbol Calculating for symbol={}",
    		    symbol
    		);
    	
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Symbol is required"
            );
        }

        return symbol.trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normaliseInterval(
            String intervalCode
    ) {
    	log.info(
    		    " normaliseInterval Calculating for intervalCode={}",
    		    intervalCode
    		);
        if (intervalCode == null
                || intervalCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Interval is required"
            );
        }

        return intervalCode.trim();
    }

}