package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.dto.FundamentalRequest;
import com.crypto.repository.MarketFundamentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crypto.config.FundamentalCollectionProperties;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FundamentalService {

    private final MarketFundamentalRepository repository;
    private final FundamentalCollectionProperties properties;

    @Transactional
    public MarketFundamental save(FundamentalRequest request) {
        return repository.save(MarketFundamental.builder()
                .symbol(request.symbol().trim().toUpperCase())
                .marketCap(request.marketCap())
                .fullyDilutedValuation(request.fullyDilutedValuation())
                .volume24h(request.volume24h())
                .circulatingSupply(request.circulatingSupply())
                .totalSupply(request.totalSupply())
                .maxSupply(request.maxSupply())
                .tier1ExchangeCount(request.tier1ExchangeCount())
                .exchangeCount(request.exchangeCount())
                .teamSupply(request.teamSupply())
                .treasurySupply(request.treasurySupply())
                .privateInvestorSupply(request.privateInvestorSupply())
                .lockedSupply(request.lockedSupply())
                .observedAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<MarketFundamental> latest(String symbol) {
        return repository.findTopBySymbolOrderByObservedAtDesc(symbol.toUpperCase());
    }

    @Transactional(readOnly = true)
    public Optional<MarketFundamental> latestAsOf(String symbol, Instant evaluatedAt) {
        String normalized = symbol.toUpperCase();
        if (evaluatedAt == null) {
            return latest(normalized);
        }
        return repository.findTopBySymbolAndObservedAtLessThanEqualOrderByObservedAtDesc(
                normalized, evaluatedAt);
    }

    public boolean isAvailable(MarketFundamental fundamental, Instant evaluatedAt) {
        if (fundamental == null || fundamental.getObservedAt() == null) return false;
        Instant reference = evaluatedAt == null ? Instant.now() : evaluatedAt;
        if (fundamental.getObservedAt().isBefore(reference.minus(properties.staleAfter()))) return false;
        return fundamental.getMarketCap() != null
                && fundamental.getMarketCap().signum() > 0
                && fundamental.getVolume24h() != null
                && fundamental.getVolume24h().signum() >= 0
                && fundamental.getCirculatingSupply() != null
                && fundamental.getCirculatingSupply().signum() > 0;
    }
}
