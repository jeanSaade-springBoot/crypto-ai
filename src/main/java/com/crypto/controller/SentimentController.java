package com.crypto.controller;

import com.crypto.domain.SentimentProviderConfig;
import com.crypto.domain.SentimentSignal;
import com.crypto.dto.*;
import com.crypto.service.SentimentCollectionService;
import com.crypto.service.SentimentProviderConfigService;
import com.crypto.service.SentimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentService service;
    private final SentimentCollectionService collectionService;
    private final SentimentProviderConfigService providerConfigService;

    @PostMapping
    public SentimentSignal save(@Valid @RequestBody SentimentRequest request) {
        return service.save(request);
    }

    @PostMapping("/analyze")
    public SentimentSignal analyze(@Valid @RequestBody SentimentTextRequest request) {
        return service.analyzeAndSave(request);
    }

    @PostMapping("/collect")
    public Map<String, String> collect() {
        return collectionService.collectAll();
    }

    @PostMapping("/providers/{provider}/collect")
    public Map<String, String> collectProvider(@PathVariable String provider) {
        return collectionService.collectProvider(provider);
    }

    @PatchMapping("/providers/{provider}")
    public SentimentProviderConfig updateProvider(
            @PathVariable String provider,
            @Valid @RequestBody SentimentProviderUpdateRequest request
    ) {
        return providerConfigService.update(provider, request);
    }

    @GetMapping("/providers/{symbol}")
    public List<SentimentProviderStatus> providers(@PathVariable String symbol) {
        Map<String, ProviderSentiment> scores = service.overview(symbol).providers().stream()
                .collect(Collectors.toMap(ProviderSentiment::provider, Function.identity(), (a, b) -> a));
        return providerConfigService.findAll().stream().map(config -> {
            ProviderSentiment score = scores.get(config.getProviderCode());
            return new SentimentProviderStatus(
                    config.getProviderCode(),
                    config.getDisplayName(),
                    config.isEnabled(),
                    config.getWeight(),
                    config.getCollectionIntervalSeconds(),
                    score == null ? BigDecimal.ZERO : score.score(),
                    score == null ? BigDecimal.ZERO : score.confidence(),
                    score == null ? 0 : score.sampleCount(),
                    score == null ? null : score.latestObservedAt(),
                    config.getLastCollectionAt(),
                    config.getLastSuccessAt(),
                    config.getLastStatus(),
                    config.getLastMessage(),
                    providerConfigService.apiKeyConfigured(config.getProviderCode()),
                    config.getApiKeyEnvVar()
            );
        }).toList();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "enabled", service.isEnabled(),
                "message", service.isEnabled()
                        ? "Sentiment analysis is enabled"
                        : "Sentiment analysis is disabled; AnalysisService uses score 0"
        );
    }

    @GetMapping("/{symbol}")
    public SentimentOverview overview(@PathVariable String symbol) {
        return service.overview(symbol);
    }
}
