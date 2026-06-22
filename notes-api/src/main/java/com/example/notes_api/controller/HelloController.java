package com.example.notes_api.controller;

import com.example.notes_api.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController   // ← 我是 Web 入口,方法返回值自动转 JSON
public class HelloController {

    private final GreetingService greetingService;   // 我需要 GreetingService

    // 构造器注入:Spring 启动时自动把 GreetingService 的实例传进来
    public HelloController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/api/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "同学") String name) {
        return Map.of(
                "message", greetingService.greet(name),
                "framework", "Spring Boot 3"
        );
    }
}
