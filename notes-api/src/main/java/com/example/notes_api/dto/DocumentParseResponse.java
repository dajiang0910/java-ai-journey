package com.example.notes_api.dto;

/**
 * 文档解析结果 —— Tika 从文件中提取的纯文本及文件元信息。
 *
 * @param filename    原始文件名
 * @param detectedType Tika 检测到的 MIME 类型（如 application/pdf）
 * @param textLength   提取文本的字符数
 * @param text         提取出的纯文本内容
 */
public record DocumentParseResponse(
        String filename,
        String detectedType,
        int textLength,
        String text
) {}
