package com.crypto.whale.controller;

import com.crypto.whale.dto.*;
import com.crypto.whale.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whales")
@RequiredArgsConstructor
public class WhaleController {
    private final WhaleTransactionService transactionService;
    private final WhaleAggregationService aggregationService;

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public int ingest(@RequestBody WhaleTransactionInput request) { return transactionService.process(request); }

    @PostMapping("/aggregate/{symbol}")
    public WhaleSentimentResult aggregate(@PathVariable String symbol) { return aggregationService.calculateAndSave(symbol); }
}
