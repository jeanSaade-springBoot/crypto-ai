package com.crypto.inspector.controller;

import com.crypto.inspector.service.TradeActivityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class TradeActivityController {
    private final TradeActivityService service;
    public TradeActivityController(TradeActivityService service) { this.service = service; }

    @GetMapping("/trade-activity")
    public String page() { return "forward:/trade-activity.html"; }

    /** On-demand only: the page itself never calls this endpoint until Search is pressed. */
    @GetMapping("/api/trade-activity")
    @ResponseBody
    public List<Map<String,Object>> search(@RequestParam(defaultValue="ALL") String symbol,
                                           @RequestParam int hours,
                                           @RequestParam(name="filter") List<String> filters) {
        return service.search(symbol, hours, filters);
    }

    @GetMapping("/api/trade-activity/symbols")
    @ResponseBody
    public List<String> symbols() { return service.symbols(); }
}
