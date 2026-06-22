package com.kakogawa.traffic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    // TOPページへのアクセス（http://localhost:8081/）をキャッチして index.html を表示する
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
