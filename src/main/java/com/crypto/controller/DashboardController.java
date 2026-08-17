package com.crypto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "forward:/dashboard.html";
    }


    @GetMapping("/catching-market")
    public String catchingMarket() {
        return "forward:/catching-market.html";
    }

    @GetMapping("/opportunity-center")
    public String opportunityCenter() {
        return "forward:/opportunity-center.html";
    }

    @GetMapping("/score-diagnostics")
    public String scoreDiagnostics() {
        return "forward:/score-diagnostics.html";
    }

    @GetMapping("/crypto-fundamentals")
    public String cryptoFundamentals() {
        return "forward:/crypto-fundamentals.html";
    }

    @GetMapping("/proven-analyzed-trades")
    public String provenAnalyzedTrades() {
        return "forward:/proven-analyzed-trades.html";
    }

    @GetMapping("/fix-registry")
    public String fixRegistry() {
        return "forward:/fix-registry.html";
    }

    @GetMapping("/system-health")
    public String systemHealth() {
        return "forward:/system-health.html";
    }
}
