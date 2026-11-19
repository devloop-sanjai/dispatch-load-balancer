package com.freightfox.dispatch.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Hidden
    @GetMapping("/")
    public String home() {
        return "redirect:/swagger-ui.html";
    }
}