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


    /**
     * FIX-059: Trade Activity-only forensic chart. It is read-only and returns the persisted
     * 1m market path, every technical-analysis signal in the selected window and completed
     * BUY→SELL lifecycle couples. Database timestamps stay UTC; the browser renders local/KSA time.
     */
    @GetMapping("/api/trade-activity/graph")
    @ResponseBody
    public Map<String,Object> graph(@RequestParam String symbol,
                                    @RequestParam int hours) {
        return service.graph(symbol, hours);
    }

    @GetMapping("/api/trade-activity/symbols")
    @ResponseBody
    public List<String> symbols() { return service.symbols(); }
}
