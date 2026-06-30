package com.example.notes_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 文档智能分析综合响应 —— Week 4 Day 5 核心产出。
 * <p>
 * 这个 record 是"企业知识库智能助手"接入层的完整输出合约，
 * 包含 AI 提取的元数据 + 程序计算的文本统计 + AI 提炼的关键句。
 * <p>
 * 对比：
 * <ul>
 *   <li>{@link NoteMetadata}：仅 AI 提取的元数据（Day 1-2）</li>
 *   <li>{@link DocumentParseResponse}：仅 Tika 解析出的文本（Day 3）</li>
 *   <li><b>本类</b>：AI 提取 + 文本统计 + 关键句，三者合一（Day 5）</li>
 * </ul>
 *
 * @param filename       原始文件名
 * @param detectedType   Tika 检测到的 MIME 类型
 * @param metadata       AI 提取的结构化元数据
 * @param statistics     文本统计信息（程序计算）
 * @param keySentences   AI 提炼的 3-5 句关键句
 * @param readingTime    预估阅读时间描述（如 "约 3 分钟"）
 */
public record DocumentAnalysisResponse(
        @JsonProperty(required = true)
        @JsonPropertyDescription("原始文件名")
        String filename,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Tika 检测到的 MIME 类型")
        String detectedType,

        @JsonProperty(required = true)
        @JsonPropertyDescription("AI 提取的结构化元数据")
        NoteMetadata metadata,

        @JsonProperty(required = true)
        @JsonPropertyDescription("文本统计信息")
        TextStatistics statistics,

        @JsonProperty(required = true)
        @JsonPropertyDescription("AI 提炼的关键句（3-5 句）")
        List<String> keySentences,

        @JsonProperty(required = true)
        @JsonPropertyDescription("预估阅读时间（如 '约 3 分钟'）")
        String readingTime
) {}
