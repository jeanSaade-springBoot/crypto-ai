package com.crypto.account.service;

import com.crypto.account.domain.CryptoAccountConfiguration;
import com.crypto.account.dto.CryptoAccountConfigurationRequest;
import com.crypto.account.dto.CryptoAccountConfigurationResponse;
import com.crypto.account.repository.CryptoAccountConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * FIX-031: resolves account configuration from the authenticated application user.
 * There is deliberately no userId in the API request; callers cannot select or overwrite
 * another user's exchange account. The authenticated username is the ownership boundary.
 */
@Service
@RequiredArgsConstructor
public class CryptoAccountConfigurationService {
    private static final String EXCHANGE = "BINANCE";
    private static final BigDecimal DEFAULT_MAX_ORDER = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_MAX_EXPOSURE = new BigDecimal("50.00");
    private static final BigDecimal DEFAULT_DAILY_LOSS = new BigDecimal("10.00");

    private final CryptoAccountConfigurationRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final CryptoCredentialCipher cipher;

    @Transactional
    public CryptoAccountConfigurationResponse getOrCreate(String username) {
        long userId = userId(username);
        CryptoAccountConfiguration config = repository.findByUserIdAndExchangeCode(userId, EXCHANGE)
                .orElseGet(() -> repository.save(defaultConfiguration(userId)));
        return response(username, config);
    }

    @Transactional
    public CryptoAccountConfigurationResponse update(String username, CryptoAccountConfigurationRequest request) {
        long userId = userId(username);
        CryptoAccountConfiguration config = repository.findByUserIdAndExchangeCode(userId, EXCHANGE)
                .orElseGet(() -> defaultConfiguration(userId));

        config.setAccountLabel(cleanLabel(request.accountLabel()));
        config.setExecutionMode(normalizeMode(request.executionMode()));
        config.setMaxOrderUsdt(positiveOrDefault(request.maxOrderUsdt(), DEFAULT_MAX_ORDER, "Maximum order USDT"));
        config.setMaxTotalExposureUsdt(positiveOrDefault(request.maxTotalExposureUsdt(), DEFAULT_MAX_EXPOSURE, "Maximum total exposure USDT"));
        config.setMaxDailyLossUsdt(positiveOrDefault(request.maxDailyLossUsdt(), DEFAULT_DAILY_LOSS, "Maximum daily loss USDT"));
        config.setMaxOpenPositions(request.maxOpenPositions() == null ? 3 : bounded(request.maxOpenPositions(), 1, 100, "Maximum open positions"));

        // Credentials are intentionally all-or-nothing. Blank fields preserve the existing secret;
        // explicit clearCredentials removes both values so partial/ambiguous API access cannot occur.
        if (request.clearCredentials()) {
            config.setApiKeyEncrypted(null);
            config.setApiSecretEncrypted(null);
            config.setApiKeyHint(null);
        } else if (hasText(request.apiKey()) || hasText(request.apiSecret())) {
            if (!hasText(request.apiKey()) || !hasText(request.apiSecret())) {
                throw new IllegalArgumentException("API key and API secret must be supplied together.");
            }
            config.setApiKeyEncrypted(cipher.encrypt(request.apiKey().trim()));
            config.setApiSecretEncrypted(cipher.encrypt(request.apiSecret().trim()));
            config.setApiKeyHint(mask(request.apiKey().trim()));
        }

        config.setUpdatedAt(Instant.now());
        if (config.getCreatedAt() == null) config.setCreatedAt(config.getUpdatedAt());
        config = repository.save(config);
        return response(username, config);
    }

    private CryptoAccountConfiguration defaultConfiguration(long userId) {
        Instant now = Instant.now();
        return CryptoAccountConfiguration.builder()
                .userId(userId)
                .exchangeCode(EXCHANGE)
                .accountLabel("Primary account")
                .executionMode("PAPER")
                .maxOrderUsdt(DEFAULT_MAX_ORDER)
                .maxTotalExposureUsdt(DEFAULT_MAX_EXPOSURE)
                .maxOpenPositions(3)
                .maxDailyLossUsdt(DEFAULT_DAILY_LOSS)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private CryptoAccountConfigurationResponse response(String username, CryptoAccountConfiguration c) {
        boolean configured = hasText(c.getApiKeyEncrypted()) && hasText(c.getApiSecretEncrypted());
        return new CryptoAccountConfigurationResponse(
                c.getId(), username, c.getExchangeCode(), c.getAccountLabel(), c.getExecutionMode(),
                configured, configured ? c.getApiKeyHint() : null,
                c.getMaxOrderUsdt(), c.getMaxTotalExposureUsdt(), c.getMaxOpenPositions(), c.getMaxDailyLossUsdt(), c.getUpdatedAt());
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username=? AND enabled=TRUE", Long.class, username);
        if (id == null) throw new IllegalStateException("Authenticated user is not available in app_user.");
        return id;
    }

    private String normalizeMode(String mode) {
        String value = hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "PAPER";
        // LIVE_MICRO is configuration-only in FIX-031. No Binance order path is enabled here.
        if (!value.equals("PAPER") && !value.equals("LIVE_MICRO")) {
            throw new IllegalArgumentException("Execution mode must be PAPER or LIVE_MICRO.");
        }
        return value;
    }

    private String cleanLabel(String label) {
        String value = hasText(label) ? label.trim() : "Primary account";
        if (value.length() > 100) throw new IllegalArgumentException("Account label must be 100 characters or fewer.");
        return value;
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal fallback, String label) {
        BigDecimal effective = value == null ? fallback : value;
        if (effective.signum() <= 0) throw new IllegalArgumentException(label + " must be greater than zero.");
        return effective;
    }

    private int bounded(int value, int min, int max, String label) {
        if (value < min || value > max) throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        return value;
    }

    private String mask(String apiKey) {
        if (apiKey.length() <= 8) return "••••" + apiKey.substring(Math.max(0, apiKey.length() - 2));
        return apiKey.substring(0, 4) + "••••••••" + apiKey.substring(apiKey.length() - 4);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
