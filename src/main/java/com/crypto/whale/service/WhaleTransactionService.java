package com.crypto.whale.service;

import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.domain.*;
import com.crypto.whale.dto.WhaleTransactionInput;
import com.crypto.whale.repository.WhaleActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WhaleTransactionService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final WhaleActivityRepository repository;
    private final WhalePriceService priceService;
    private final WhaleProperties properties;

    @Transactional
    public int process(WhaleTransactionInput input) {
        validate(input);
        String symbol = normalizeAsset(input.asset()) + "USDT";
        WhaleMovementType type = classify(input.fromLabel(), input.toLabel());
        boolean trackSender = type != WhaleMovementType.EXCHANGE_OUTFLOW;
        String wallet = normalizeAddress(trackSender ? input.fromAddress() : input.toAddress());
        String counterparty = normalizeAddress(trackSender ? input.toAddress() : input.fromAddress());
        String walletLabel = trackSender ? input.fromLabel() : input.toLabel();
        String counterpartyLabel = trackSender ? input.toLabel() : input.fromLabel();
        if (wallet == null) wallet = counterparty == null ? "UNKNOWN" : counterparty;

        BigDecimal score = score(type, input.usdValue());
        BigDecimal confidence = confidence(input.fromLabel(), input.toLabel(), type);
        BigDecimal price = priceService.latestPrice(symbol);
        int inserted = 0;
        for (String configuredHorizon : properties.evaluation().horizons()) {
            WhaleEvaluationHorizon horizon = WhaleEvaluationHorizon.fromCode(configuredHorizon);
            if (repository.existsByBlockchainAndTransactionHashAndWalletAddressAndEvaluationHorizon(
                    input.blockchain(), input.transactionHash(), wallet, horizon)) continue;
            BigDecimal weight = latestWeight(wallet, symbol, horizon);
            Instant now = Instant.now();
            repository.save(WhaleActivity.builder()
                    .blockchain(input.blockchain().toUpperCase(Locale.ROOT))
                    .transactionHash(input.transactionHash())
                    .walletAddress(wallet)
                    .counterpartyAddress(counterparty)
                    .walletLabel(walletLabel)
                    .counterpartyLabel(counterpartyLabel)
                    .symbol(symbol)
                    .asset(normalizeAsset(input.asset()))
                    .movementType(type)
                    .amount(input.amount())
                    .usdValue(input.usdValue())
                    .transactionScore(score)
                    .transactionConfidence(confidence)
                    .priceAtSignal(price)
                    .evaluationHorizon(horizon)
                    .evaluationDueAt(input.observedAt().plus(horizon.duration()))
                    .evaluationResult(WhaleEvaluationResult.PENDING)
                    .whaleAccuracy(BigDecimal.ZERO)
                    .whaleAverageQuality(BigDecimal.ZERO)
                    .whaleLearnedWeight(weight)
                    .observedAt(input.observedAt())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            inserted++;
        }
        return inserted;
    }

    private BigDecimal latestWeight(String wallet, String symbol, WhaleEvaluationHorizon horizon) {
        return repository.findFirstByWalletAddressAndSymbolAndEvaluationHorizonAndEvaluationResultInOrderByEvaluatedAtDesc(
                        wallet, symbol, horizon, List.of(WhaleEvaluationResult.CORRECT, WhaleEvaluationResult.INCORRECT, WhaleEvaluationResult.INCONCLUSIVE))
                .map(WhaleActivity::getWhaleLearnedWeight)
                .orElse(properties.learning().initialWeight());
    }

    private WhaleMovementType classify(String from, String to) {
        boolean fromExchange = isExchange(from); boolean toExchange = isExchange(to);
        if (!fromExchange && toExchange) return WhaleMovementType.EXCHANGE_INFLOW;
        if (fromExchange && !toExchange) return WhaleMovementType.EXCHANGE_OUTFLOW;
        if (contains(from, "mint")) return WhaleMovementType.MINT;
        if (contains(to, "burn") || contains(to, "null")) return WhaleMovementType.BURN;
        return WhaleMovementType.WALLET_TO_WALLET;
    }

    private BigDecimal score(WhaleMovementType type, BigDecimal usd) {
        BigDecimal direction = switch (type) {
            case EXCHANGE_INFLOW -> new BigDecimal("-0.75");
            case EXCHANGE_OUTFLOW -> new BigDecimal("0.75");
            case BURN -> new BigDecimal("0.40");
            case MINT -> new BigDecimal("-0.40");
            default -> BigDecimal.ZERO;
        };
        BigDecimal magnitude = usd.divide(new BigDecimal("50000000"), MC).min(BigDecimal.ONE).max(new BigDecimal("0.10"));
        return direction.multiply(magnitude, MC).max(BigDecimal.ONE.negate()).min(BigDecimal.ONE);
    }

    private BigDecimal confidence(String from, String to, WhaleMovementType type) {
        if (type == WhaleMovementType.WALLET_TO_WALLET || type == WhaleMovementType.UNKNOWN) return new BigDecimal("0.30");
        return (isExchange(from) || isExchange(to)) ? new BigDecimal("0.85") : new BigDecimal("0.60");
    }
    private boolean isExchange(String value) { return contains(value, "binance") || contains(value, "coinbase") || contains(value, "kraken") || contains(value, "okx") || contains(value, "bybit") || contains(value, "kucoin"); }
    private boolean contains(String value, String term) { return value != null && value.toLowerCase(Locale.ROOT).contains(term); }
    private String normalizeAsset(String asset) { return asset.toUpperCase(Locale.ROOT).replace("USDT", "").trim(); }
    private String normalizeAddress(String address) { return address == null || address.isBlank() ? null : address.trim().toLowerCase(Locale.ROOT); }
    private void validate(WhaleTransactionInput input) {
        if (input == null || input.transactionHash() == null || input.asset() == null || input.usdValue() == null || input.observedAt() == null)
            throw new IllegalArgumentException("Whale transaction hash, asset, USD value and observed time are required");
    }
}
