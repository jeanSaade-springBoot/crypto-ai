package com.crypto.service;

import com.crypto.config.AnalysisScoringProperties;
import com.crypto.domain.Candle;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandleDataQualityService {

    private final CandleRepository candleRepository;
    private final AnalysisScoringProperties properties;

    @Transactional(readOnly = true)
    public CandleDataQualityResult validate(String symbol, String intervalCode) {
        int minimum = properties.dataQuality().minimumCandles();
        List<Candle> candles = new ArrayList<>(candleRepository.findClosedCandles(
                symbol, intervalCode, PageRequest.of(0, minimum)
        ));
        candles.sort(Comparator.comparing(Candle::getOpenTime));

        List<String> warnings = new ArrayList<>();
        int invalid = 0;
        for (Candle candle : candles) {
            boolean invalidOhlc = candle.getOpenPrice() == null
                    || candle.getHighPrice() == null
                    || candle.getLowPrice() == null
                    || candle.getClosePrice() == null
                    || candle.getVolume() == null
                    || candle.getOpenPrice().signum() <= 0
                    || candle.getHighPrice().compareTo(candle.getLowPrice()) < 0
                    || candle.getClosePrice().compareTo(candle.getLowPrice()) < 0
                    || candle.getClosePrice().compareTo(candle.getHighPrice()) > 0
                    || candle.getVolume().signum() < 0;
            if (invalidOhlc) {
                invalid++;
            }
        }

        Duration expectedStep = intervalDuration(intervalCode);
        int missing = 0;
        if (!expectedStep.isZero()) {
            for (int index = 1; index < candles.size(); index++) {
                Duration actual = Duration.between(candles.get(index - 1).getOpenTime(), candles.get(index).getOpenTime());
                if (actual.compareTo(expectedStep) > 0) {
                    long steps = actual.toMillis() / expectedStep.toMillis();
                    missing += Math.max(0, (int) steps - 1);
                }
            }
        } else {
            warnings.add("Unsupported interval for gap validation: " + intervalCode);
        }

        if (candles.size() < minimum) {
            warnings.add("Insufficient history: " + candles.size() + "/" + minimum + " closed candles");
        }
        if (missing > 0) {
            warnings.add("Missing " + missing + " candle(s) in the analysis window");
        }
        if (invalid > 0) {
            warnings.add("Found " + invalid + " candle(s) with invalid OHLCV data");
        }

        boolean valid = candles.size() >= minimum
                && missing <= properties.dataQuality().maximumMissingCandles()
                && (!properties.dataQuality().blockOnInvalidOhlc() || invalid == 0);

        return new CandleDataQualityResult(valid, minimum, candles.size(), missing, invalid, List.copyOf(warnings));
    }

    private Duration intervalDuration(String intervalCode) {
        if (intervalCode == null || intervalCode.length() < 2) {
            return Duration.ZERO;
        }
        try {
            long amount = Long.parseLong(intervalCode.substring(0, intervalCode.length() - 1));
            char unit = intervalCode.charAt(intervalCode.length() - 1);
            return switch (unit) {
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(amount * 7);
                default -> Duration.ZERO;
            };
        } catch (NumberFormatException exception) {
            return Duration.ZERO;
        }
    }
}
