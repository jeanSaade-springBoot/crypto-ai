package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.dto.FundamentalRequest;
import com.crypto.repository.MarketFundamentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FundamentalService {

    private final MarketFundamentalRepository repository;

    @Transactional
    public MarketFundamental save(FundamentalRequest request) {
        return repository.save(MarketFundamental.builder()
                .symbol(request.symbol().trim().toUpperCase())
                .marketCap(request.marketCap())
                .fullyDilutedValuation(request.fullyDilutedValuation())
                .volume24h(request.volume24h())
                .circulatingSupply(request.circulatingSupply())
                .totalSupply(request.totalSupply())
                .observedAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<MarketFundamental> latest(String symbol) {
        return repository.findTopBySymbolOrderByObservedAtDesc(symbol.toUpperCase());
    }
}
