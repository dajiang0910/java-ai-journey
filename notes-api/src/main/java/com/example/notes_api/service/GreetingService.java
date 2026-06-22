package com.example.notes_api.service;

import org.springframework.stereotype.Service;

@Service   // ← 贴上标签:告诉容器"造一个我,放进去"
public class GreetingService {
    public String greet(String name) {
        return "你好, " + name + "!欢迎来到 Spring Boot 世界";
    }
}
