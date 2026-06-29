package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Slug 生成请求 DTO。
 * <p>
 * Slug = URL 友好的短标识符，如 "Spring AI 起步" → "spring-ai-quickstart"。
 * 仅需一个输入字段，但用 record 而非裸 String 是为了统一校验风格 + 未来扩展。
 */
public record SlugRequest(
        @NotBlank(message = "标题不能为空")
        String title
) {}
