package com.crypto.account.service;

import com.crypto.account.domain.CryptoAccountConfiguration;
import com.crypto.account.dto.CryptoAccountConfigurationRequest;
import com.crypto.account.repository.CryptoAccountConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CryptoAccountConfigurationServiceTest {

    @Test
    void configurationIsOwnedByAuthenticatedUsernameAndNeverReturnsRawSecret() {
        CryptoAccountConfigurationRepository repository = mock(CryptoAccountConfigurationRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
        CryptoCredentialCipher cipher = new CryptoCredentialCipher(Base64.getEncoder().encodeToString(key));
        CryptoAccountConfigurationService service = new CryptoAccountConfigurationService(repository, jdbc, cipher);

        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("jean"))).thenReturn(42L);
        when(repository.findByUserIdAndExchangeCode(42L, "BINANCE")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            CryptoAccountConfiguration c = invocation.getArgument(0);
            c.setId(7L);
            if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
            return c;
        });

        var response = service.update("jean", new CryptoAccountConfigurationRequest(
                "Small Binance", "LIVE_MICRO", "abcd1234KEY", "super-secret", false,
                new BigDecimal("10"), new BigDecimal("50"), 3, new BigDecimal("10")));

        assertThat(response.username()).isEqualTo("jean");
        assertThat(response.executionMode()).isEqualTo("LIVE_MICRO");
        assertThat(response.credentialsConfigured()).isTrue();
        assertThat(response.apiKeyMasked()).doesNotContain("1234KEY");

        verify(repository).save(argThat(c -> c.getUserId().equals(42L)
                && c.getApiKeyEncrypted() != null && !c.getApiKeyEncrypted().contains("abcd1234KEY")
                && c.getApiSecretEncrypted() != null && !c.getApiSecretEncrypted().contains("super-secret")));
    }
}
