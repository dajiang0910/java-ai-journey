package com.example.notes_api.dto;

/**
 * 「智能笔记发布助手」响应 —— AI 生成的元数据 + Token 成本。
 * <p>
 * 这是 Day 6 综合实战的响应体 DTO，一次返回 AI 的三项产出 +
 * 三步调用的聚合 Token 用量，让调用方能感知完整成本。
 *
 * @param title        AI 生成的标题
 * @param summary      AI 生成的摘要
 * @param translation  AI 翻译的英文版本
 * @param tokens       三次 LLM 调用的聚合 Token 用量
 */
public record SmartNoteResponse(
        String title,
        String summary,
        String translation,
        ChatCostResponse.TokenUsage tokens
) {}
