package com.crypto.administration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdministrationController {
    @GetMapping({"/administration", "/admin"})
    public String administration() {
        return "forward:/administration.html";
    }
}
