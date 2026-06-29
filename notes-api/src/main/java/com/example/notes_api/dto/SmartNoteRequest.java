package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 「智能笔记发布助手」请求 —— 只需传原始内容。
 * <p>
 * AI 会自动生成：标题 + 摘要 + 英文翻译。
 * 这是 Day 6 综合实战的请求体 DTO —— 结构极简，AI 做重活。
 */
public record SmartNoteRequest(
        @NotBlank(message = "笔记内容不能为空")
        String content
) {}
