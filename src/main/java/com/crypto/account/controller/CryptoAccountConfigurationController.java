package com.crypto.account.controller;

import com.crypto.account.dto.CryptoAccountConfigurationRequest;
import com.crypto.account.dto.CryptoAccountConfigurationResponse;
import com.crypto.account.service.CryptoAccountConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** FIX-031: current-user-only Crypto Account API. */
@RestController
@RequestMapping("/api/crypto-account")
@RequiredArgsConstructor
public class CryptoAccountConfigurationController {
    private final CryptoAccountConfigurationService service;

    @GetMapping
    public CryptoAccountConfigurationResponse current(Principal principal) {
        return service.getOrCreate(principal.getName());
    }

    @PutMapping
    public CryptoAccountConfigurationResponse update(Principal principal,
                                                      @RequestBody CryptoAccountConfigurationRequest request) {
        return service.update(principal.getName(), request);
    }
}
