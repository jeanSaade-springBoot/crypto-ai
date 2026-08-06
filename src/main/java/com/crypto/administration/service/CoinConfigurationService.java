package com.crypto.administration.service;

import com.crypto.administration.domain.CoinConfiguration;
import com.crypto.administration.dto.CoinConfigurationView;
import com.crypto.administration.repository.CoinConfigurationRepository;
import com.crypto.client.binance.BinanceMarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CoinConfigurationService {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{2,15}USDT$");

    private final CoinConfigurationRepository repository;
    private final BinanceMarketDataClient marketDataClient;

    @Transactional(readOnly = true)
    public List<CoinConfigurationView> findAll() {
        return repository.findAllByOrderBySystemDefaultDescSymbolAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> enabledSymbols() {
        return repository.findByEnabledTrueOrderBySymbolAsc().stream()
                .map(CoinConfiguration::getSymbol)
                .toList();
    }

    @Transactional
    public CoinConfigurationView add(String rawSymbol) {
        String symbol = normalize(rawSymbol);
        validateFormat(symbol);
        repository.findBySymbol(symbol).ifPresent(existing -> {
            throw new IllegalArgumentException("Coin " + symbol + " already exists");
        });

        // A small public kline request verifies that Binance Spot recognizes the pair.
        marketDataClient.getKlines(symbol, "1m", 1);

        CoinConfiguration saved = repository.save(CoinConfiguration.builder()
                .symbol(symbol)
                .enabled(true)
                .systemDefault(false)
                .build());
        return toView(saved);
    }

    @Transactional
    public CoinConfigurationView setEnabled(Long id, boolean enabled) {
        CoinConfiguration coin = findRequired(id);
        coin.setEnabled(enabled);
        return toView(repository.save(coin));
    }

    @Transactional
    public void remove(Long id) {
        CoinConfiguration coin = findRequired(id);
        if (coin.isSystemDefault()) {
            throw new IllegalArgumentException("Default coins cannot be removed; disable the coin instead");
        }
        repository.delete(coin);
    }

    private CoinConfiguration findRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coin configuration was not found"));
    }

    private String normalize(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace("/", "").replace("-", "");
        return normalized.endsWith("USDT") ? normalized : normalized + "USDT";
    }

    private void validateFormat(String symbol) {
        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new IllegalArgumentException("Enter a valid USDT pair such as ADAUSDT");
        }
    }

    private CoinConfigurationView toView(CoinConfiguration coin) {
        return new CoinConfigurationView(
                coin.getId(), coin.getSymbol(), coin.isEnabled(), coin.isSystemDefault(), !coin.isSystemDefault());
    }
}
