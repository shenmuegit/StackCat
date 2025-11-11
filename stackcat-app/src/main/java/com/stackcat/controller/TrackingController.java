package com.stackcat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TrackingController {
    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/tracking")
    public String tracking() {
        return "tracking";
    }
}

