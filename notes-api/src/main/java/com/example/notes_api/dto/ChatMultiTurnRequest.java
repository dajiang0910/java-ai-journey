package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 多轮对话请求 DTO。
 * <p>
 * 相比 Day 1 的 ChatRequest，多了 {@code conversationId}：
 * <ul>
 *   <li>{@code conversationId} —— 对话会话标识，同一个 conversationId 共享历史</li>
 *   <li>{@code message} —— 当前轮的用户输入</li>
 * </ul>
 * <p>
 * 类比：conversationId 就像 HTTP Session ID，区别不同的对话上下文。
 */
@Schema(description = "多轮对话请求")
public record ChatMultiTurnRequest(
        @NotBlank(message = "对话ID不能为空")
        @Schema(description = "对话会话标识，同一个 ID 共享对话历史", example = "conv-001")
        String conversationId,

        @NotBlank(message = "消息不能为空")
        @Schema(description = "当前轮的用户输入", example = "我叫什么名字？")
        String message
) {}
