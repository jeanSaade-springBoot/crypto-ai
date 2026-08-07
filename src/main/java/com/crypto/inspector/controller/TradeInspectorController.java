package com.crypto.inspector.controller;

import com.crypto.dto.TradeInspectorResponse;
import com.crypto.inspector.service.TradeInspectorService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TradeInspectorController {

    private final TradeInspectorService service;

    public TradeInspectorController(TradeInspectorService service) {
        this.service = service;
    }

    @GetMapping("/trade-inspector")
    public String page() {
        return "forward:/trade-inspector.html";
    }

    @GetMapping("/api/trade-inspector")
    @ResponseBody
    public TradeInspectorResponse inspect(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return service.inspect(symbol, limit);
    }
}
