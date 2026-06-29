package com.example.notes_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 摘要请求 DTO。
 * <p>
 * 第一个带数值校验的 DTO —— maxWords 有 @Min/@Max 约束，
 * 防止用户传负数或超过 500 字。
 */
public record SummarizeRequest(
        @NotBlank(message = "待摘要内容不能为空")
        String content,

        @Min(value = 1, message = "摘要字数至少为 1")
        @Max(value = 500, message = "摘要字数最多 500")
        int maxWords
) {}
