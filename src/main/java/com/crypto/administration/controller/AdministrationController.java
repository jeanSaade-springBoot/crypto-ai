package com.crypto.administration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdministrationController {
    @GetMapping({"/administration", "/admin"})
    public String administration() {
        return "forward:/administration.html";
    }

    // FIX-032: Paper Wallet and Binance are first-class pages, not Administration subsections.
    @GetMapping("/wallet")
    public String wallet() { return "forward:/wallet.html"; }

    @GetMapping("/binance")
    public String binance() { return "forward:/binance.html"; }
}
