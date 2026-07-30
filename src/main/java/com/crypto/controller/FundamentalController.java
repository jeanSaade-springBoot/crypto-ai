package com.crypto.controller;

import com.crypto.domain.MarketFundamental;
import com.crypto.dto.FundamentalRequest;
import com.crypto.service.FundamentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fundamentals")
@RequiredArgsConstructor
public class FundamentalController {

    private final FundamentalService service;

    @PostMapping
    public MarketFundamental save(@Valid @RequestBody FundamentalRequest request) {
        return service.save(request);
    }
}
