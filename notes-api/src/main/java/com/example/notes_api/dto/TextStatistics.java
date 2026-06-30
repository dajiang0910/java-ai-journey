package com.example.notes_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 文档文本统计信息 —— 纯计算得出，不依赖 AI。
 *
 * @param totalChars      总字符数
 * @param totalWords      总词数（中文按字数估算，英文按空格分词）
 * @param totalSentences  句子数
 * @param totalParagraphs 段落数
 * @param estimatedMinutes 预估阅读时间（分钟）
 */
public record TextStatistics(
        @JsonProperty(required = true)
        @JsonPropertyDescription("总字符数（含空格和标点）")
        int totalChars,

        @JsonProperty(required = true)
        @JsonPropertyDescription("总词数（中文按字数，英文按空格分词）")
        int totalWords,

        @JsonProperty(required = true)
        @JsonPropertyDescription("句子数（按 .。!！?？ 分隔）")
        int totalSentences,

        @JsonProperty(required = true)
        @JsonPropertyDescription("段落数（按连续空行分隔）")
        int totalParagraphs,

        @JsonProperty(required = true)
        @JsonPropertyDescription("预估阅读时间（分钟），假设中文阅读速度 400 字/分钟")
        int estimatedMinutes
) {}
