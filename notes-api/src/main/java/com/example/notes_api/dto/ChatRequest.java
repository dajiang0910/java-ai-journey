package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求 —— 前端只需传一条消息。
 * 用 record 而非 class：不可变数据传输对象，完美匹配 @RequestBody 反序列化。
 */
public record ChatRequest(
        @NotBlank(message = "消息不能为空")
        String message
) {}
