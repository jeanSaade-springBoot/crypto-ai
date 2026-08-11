package com.crypto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    @GetMapping("/opportunity-center")
    public String opportunityCenter() {
        return "forward:/opportunity-center.html";
    }

    @GetMapping("/score-diagnostics")
    public String scoreDiagnostics() {
        return "forward:/score-diagnostics.html";
    }
}
