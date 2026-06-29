package com.example.notes_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 结构化提取出的笔记元数据。
 * <p>
 * 这个 record 就是 LLM 输出的"合同"——Spring AI 会把它变成 JSON Schema
 * 发给 LLM，LLM 必须按这个结构回复。
 * <p>
 * {@link JsonProperty @JsonProperty} 标注的 required=true 字段，
 * 会被写入 JSON Schema 的 required 数组，约束 LLM 必须输出这些字段。
 * <p>
 * {@link JsonPropertyDescription @JsonPropertyDescription} 标注的描述，
 * 会被写入 JSON Schema 的 description 字段，帮助 LLM 理解每个字段的语义。
 * Day 2 新增：所有字段添加 description，category 添加 enum 约束。
 */
public record NoteMetadata(
        @JsonProperty(required = true, value = "title")
        @JsonPropertyDescription("内容的核心标题，不超过 50 字，简洁概括主题")
        String title,

        @JsonProperty(required = true, value = "keywords")
        @JsonPropertyDescription("3-5 个关键词，用于检索和分类")
        List<String> keywords,

        @JsonProperty(required = true, value = "category")
        @JsonPropertyDescription("内容分类，必须是以下之一：技术、管理、产品、设计、其他")
        String category,

        @JsonPropertyDescription("内容难度，可选值：入门、中级、高级；根据内容的专业程度判断")
        String difficulty,

        @JsonPropertyDescription("一句话摘要，不超过 100 字，概括核心内容")
        String summary
) {}