package com.example.notes_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 带 Token 用量信息的对话响应。
 * <p>
 * Day 5 新增：相比 Day 1 的纯文本回复，多了 Token 用量统计，
 * 让调用方能感知每次 LLM 调用的成本。
 * <p>
 * 典型用法：前端展示"本次对话消耗 230 tokens（输入 150 + 输出 80）"。
 *
 * @see com.example.notes_api.service.ChatService#chatWithCost(String)
 */
@Schema(description = "带 Token 成本的对话响应")
public record ChatCostResponse(
        @Schema(description = "AI 回复文本", example = "你好！有什么可以帮助你的？")
        String reply,

        @Schema(description = "Token 用量详情")
        TokenUsage tokens
) {
    /**
     * Token 用量子对象。
     * <p>
     * 三个字段对应 OpenAI 协议的 usage 对象：
     * <ul>
     *   <li>{@code prompt_tokens} —— 输入 Token（用户消息 + 系统提示词 + 历史消息）</li>
     *   <li>{@code completion_tokens} —— 输出 Token（AI 生成的回复）</li>
     *   <li>{@code total_tokens} —— 总量 = 输入 + 输出</li>
     * </ul>
     * <p>
     * 百炼 qwen-turbo 参考定价（2025）：
     * <ul>
     *   <li>输入：¥0.0005 / 1K tokens</li>
     *   <li>输出：¥0.001 / 1K tokens</li>
     * </ul>
     */
    @Schema(description = "Token 用量统计")
    public record TokenUsage(
            @Schema(description = "输入 Token 数（用户消息 + 系统提示词 + 历史）", example = "150")
            Integer inputTokens,

            @Schema(description = "输出 Token 数（AI 生成的回复）", example = "80")
            Integer outputTokens,

            @Schema(description = "总 Token 数（输入 + 输出）", example = "230")
            Integer totalTokens
    ) {}
}
