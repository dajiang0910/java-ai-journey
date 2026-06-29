package com.example.notes_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LLM 结构化提取出的笔记元数据。
 * <p>
 * 这个 record 就是 LLM 输出的"合同"——Spring AI 会把它变成 JSON Schema
 * 发给 LLM，LLM 必须按这个结构回复。
 * <p>
 * {@link JsonProperty @JsonProperty} 标注的 required=true 字段，
 * 会被写入 JSON Schema 的 required 数组，约束 LLM 必须输出这些字段。
 */
public record NoteMetadata(
        @JsonProperty(required = true, value = "title")
        String title,

        @JsonProperty(required = true, value = "keywords")
        List<String> keywords,

        @JsonProperty(required = true, value = "category")
        String category,

        String difficulty,      // 可选字段，不标 required

        String summary
) {}