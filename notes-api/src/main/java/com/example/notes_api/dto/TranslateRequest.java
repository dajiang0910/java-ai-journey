package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 翻译请求 DTO。
 * <p>
 * 和 Day 1 的 ChatRequest 一样是 record，但多了 targetLanguage 字段，
 * 让用户可以指定目标语言。
 */
public record TranslateRequest(
        @NotBlank(message = "待翻译文本不能为空")
        String text,

        @NotBlank(message = "目标语言不能为空")
        String targetLanguage
) {}
