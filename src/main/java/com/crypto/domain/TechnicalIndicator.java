package com.crypto.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "technical_indicator",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_technical_indicator_candle",
                        columnNames = {
                                "symbol",
                                "interval_code",
                                "candle_open_time"
                        }
                )
        }
)
public class TechnicalIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "interval_code", nullable = false, length = 10)
    private String intervalCode;

    @Column(name = "candle_open_time", nullable = false)
    private Instant candleOpenTime;

    @Column(name = "close_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal closePrice;

    @Column(name = "sma_20", precision = 30, scale = 12)
    private BigDecimal sma20;

    @Column(name = "ema_20", precision = 30, scale = 12)
    private BigDecimal ema20;

    @Column(name = "ema_50", precision = 30, scale = 12)
    private BigDecimal ema50;

    @Column(name = "ema_200", precision = 30, scale = 12)
    private BigDecimal ema200;

    @Column(name = "rsi_14", precision = 20, scale = 8)
    private BigDecimal rsi14;

    @Column(name = "macd", precision = 30, scale = 12)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 30, scale = 12)
    private BigDecimal macdSignal;

    @Column(name = "macd_histogram", precision = 30, scale = 12)
    private BigDecimal macdHistogram;

    @Column(name = "bollinger_middle", precision = 30, scale = 12)
    private BigDecimal bollingerMiddle;

    @Column(name = "bollinger_upper", precision = 30, scale = 12)
    private BigDecimal bollingerUpper;

    @Column(name = "bollinger_lower", precision = 30, scale = 12)
    private BigDecimal bollingerLower;

    @Column(name = "bollinger_bandwidth", precision = 20, scale = 8)
    private BigDecimal bollingerBandwidth;

    @Column(name = "atr_14", precision = 30, scale = 12)
    private BigDecimal atr14;

    @Column(name = "volume_sma_20", precision = 30, scale = 12)
    private BigDecimal volumeSma20;

    @Column(name = "relative_volume", precision = 20, scale = 8)
    private BigDecimal relativeVolume;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getIntervalCode() {
        return intervalCode;
    }

    public void setIntervalCode(String intervalCode) {
        this.intervalCode = intervalCode;
    }

    public Instant getCandleOpenTime() {
        return candleOpenTime;
    }

    public void setCandleOpenTime(Instant candleOpenTime) {
        this.candleOpenTime = candleOpenTime;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getSma20() {
        return sma20;
    }

    public void setSma20(BigDecimal sma20) {
        this.sma20 = sma20;
    }

    public BigDecimal getEma20() {
        return ema20;
    }

    public void setEma20(BigDecimal ema20) {
        this.ema20 = ema20;
    }

    public BigDecimal getEma50() {
        return ema50;
    }

    public void setEma50(BigDecimal ema50) {
        this.ema50 = ema50;
    }

    public BigDecimal getEma200() {
        return ema200;
    }

    public void setEma200(BigDecimal ema200) {
        this.ema200 = ema200;
    }

    public BigDecimal getRsi14() {
        return rsi14;
    }

    public void setRsi14(BigDecimal rsi14) {
        this.rsi14 = rsi14;
    }

    public BigDecimal getMacd() {
        return macd;
    }

    public void setMacd(BigDecimal macd) {
        this.macd = macd;
    }

    public BigDecimal getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(BigDecimal macdSignal) {
        this.macdSignal = macdSignal;
    }

    public BigDecimal getMacdHistogram() {
        return macdHistogram;
    }

    public void setMacdHistogram(BigDecimal macdHistogram) {
        this.macdHistogram = macdHistogram;
    }

    public BigDecimal getBollingerMiddle() {
        return bollingerMiddle;
    }

    public void setBollingerMiddle(BigDecimal bollingerMiddle) {
        this.bollingerMiddle = bollingerMiddle;
    }

    public BigDecimal getBollingerUpper() {
        return bollingerUpper;
    }

    public void setBollingerUpper(BigDecimal bollingerUpper) {
        this.bollingerUpper = bollingerUpper;
    }

    public BigDecimal getBollingerLower() {
        return bollingerLower;
    }

    public void setBollingerLower(BigDecimal bollingerLower) {
        this.bollingerLower = bollingerLower;
    }

    public BigDecimal getBollingerBandwidth() {
        return bollingerBandwidth;
    }

    public void setBollingerBandwidth(BigDecimal bollingerBandwidth) {
        this.bollingerBandwidth = bollingerBandwidth;
    }

    public BigDecimal getAtr14() {
        return atr14;
    }

    public void setAtr14(BigDecimal atr14) {
        this.atr14 = atr14;
    }

    public BigDecimal getVolumeSma20() {
        return volumeSma20;
    }

    public void setVolumeSma20(BigDecimal volumeSma20) {
        this.volumeSma20 = volumeSma20;
    }

    public BigDecimal getRelativeVolume() {
        return relativeVolume;
    }

    public void setRelativeVolume(BigDecimal relativeVolume) {
        this.relativeVolume = relativeVolume;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}