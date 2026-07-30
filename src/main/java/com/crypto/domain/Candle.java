package com.crypto.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "candle",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_candle_symbol_interval_open_time",
            columnNames = {"symbol", "interval_code", "open_time"}
        )
    }
)
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "interval_code", nullable = false, length = 10)
    private String intervalCode;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(name = "open_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 38, scale = 12)
    private BigDecimal volume;

    @Column(name = "quote_asset_volume", precision = 38, scale = 12)
    private BigDecimal quoteAssetVolume;

    @Column(name = "number_of_trades")
    private Long numberOfTrades;

    @Column(name = "taker_buy_base_volume", precision = 38, scale = 12)
    private BigDecimal takerBuyBaseVolume;

    @Column(name = "taker_buy_quote_volume", precision = 38, scale = 12)
    private BigDecimal takerBuyQuoteVolume;

    @Column(nullable = false)
    private boolean closed;

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

    public Instant getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Instant openTime) {
        this.openTime = openTime;
    }

    public Instant getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Instant closeTime) {
        this.closeTime = closeTime;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getQuoteAssetVolume() {
        return quoteAssetVolume;
    }

    public void setQuoteAssetVolume(BigDecimal quoteAssetVolume) {
        this.quoteAssetVolume = quoteAssetVolume;
    }

    public Long getNumberOfTrades() {
        return numberOfTrades;
    }

    public void setNumberOfTrades(Long numberOfTrades) {
        this.numberOfTrades = numberOfTrades;
    }

    public BigDecimal getTakerBuyBaseVolume() {
        return takerBuyBaseVolume;
    }

    public void setTakerBuyBaseVolume(BigDecimal takerBuyBaseVolume) {
        this.takerBuyBaseVolume = takerBuyBaseVolume;
    }

    public BigDecimal getTakerBuyQuoteVolume() {
        return takerBuyQuoteVolume;
    }

    public void setTakerBuyQuoteVolume(BigDecimal takerBuyQuoteVolume) {
        this.takerBuyQuoteVolume = takerBuyQuoteVolume;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}