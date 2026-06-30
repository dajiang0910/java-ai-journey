package com.example.notes_api.service;

import com.example.notes_api.dto.NoteMetadata;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析服务 —— 基于 Apache Tika 从多格式文档中提取纯文本。
 * <p>
 * Tika 是"文档解析的 JDBC"：同一套 API 解析 PDF、Word (docx)、Markdown、
 * HTML、PPT、Excel 等 1000+ 种格式，内部自动检测格式并选择对应解析器。
 * <p>
 * 位置：知识库"接入层"的第一环 ——
 * 文档上传 → {@link #parse(InputStream, String)} → 纯文本 → {@link StructuredExtractService#extractV2(String)} → 结构化元数据
 *
 * @see StructuredExtractService 下游：结构化提取服务
 * @see DocumentAnalysisService 下游：智能分析服务（Day 5）
 */
@Service
public class DocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    /**
     * Tika 实例 —— 线程安全，复用即可。
     * 内部维护了所有注册的解析器（PDFBox、POI 等），
     * 调用 parseToString 时自动检测并路由到正确的解析器。
     */
    private final Tika tika = new Tika();

    private final StructuredExtractService extractService;

    public DocumentParseService(StructuredExtractService extractService) {
        this.extractService = extractService;
    }

    /**
     * 解析结果 —— 包含纯文本 + Tika 检测到的 MIME 类型（Day 5 新增，供 analyze 使用）。
     */
    public record ParseResult(String text, String detectedMimeType) {}

    // ================================================================
    // 公开 API
    // ================================================================

    /**
     * 解析文档内容为纯文本。
     * <p>
     * Tika 内部做三件事：
     * <ol>
     *   <li><b>格式检测</b>：读文件头 magic bytes（如 PDF 的 "%PDF"，docx 的 "PK"），
     *       不依赖文件扩展名</li>
     *   <li><b>解析器路由</b>：根据检测到的 MIME 类型选择对应解析器</li>
     *   <li><b>文本提取</b>：解析器从二进制/标记格式中提取纯文本</li>
     * </ol>
     *
     * @param inputStream 文件输入流（不关闭，由调用方管理）
     * @param originalFilename 原始文件名（用于日志和元数据）
     * @return 提取出的纯文本内容
     * @throws IOException 如果文件无法解析
     */
    public String parse(InputStream inputStream, String originalFilename) throws IOException {
        return parseWithMetadata(inputStream, originalFilename).text();
    }

    /**
     * 解析文档并返回文本 + 检测到的 MIME 类型（Day 5 新增）。
     * <p>
     * 相比 {@link #parse(InputStream, String)}，额外返回 Tika 检测到的 Content-Type，
     * 供下游 {@link DocumentAnalysisService} 使用。
     */
    public ParseResult parseWithMetadata(InputStream inputStream, String originalFilename) throws IOException {
        log.info("开始解析文档：{}", originalFilename);

        Metadata metadata = new Metadata();
        metadata.set("resourceName", originalFilename);

        String text;
        try {
            text = tika.parseToString(inputStream, metadata);
        } catch (org.apache.tika.exception.TikaException e) {
            throw new IOException("Tika 解析失败：" + originalFilename, e);
        }

        String detectedType = metadata.get("Content-Type");
        log.info("检测到文件类型：{}（文件：{}）", detectedType, originalFilename);
        log.info("解析完成：{}，提取文本长度：{} 字符", originalFilename, text.length());

        if (text.isBlank()) {
            log.warn("警告：{} 解析结果为空，可能文件无文本内容或为纯图片 PDF", originalFilename);
        }

        return new ParseResult(text, detectedType != null ? detectedType : "unknown");
    }

    /**
     * 完整链路：上传文档 → 解析为文本 → AI 提取结构化元数据。
     * <p>
     * 这就是 Week 4 目标产出的"接入层"完整闭环：
     * <pre>
     * MultipartFile → Tika → 纯文本 → extractV2() → NoteMetadata
     * </pre>
     *
     * @param inputStream 文件输入流
     * @param originalFilename 原始文件名
     * @return 结构化元数据（标题/关键词/分类/难度/摘要）
     * @throws IOException 如果文件解析失败
     */
    public NoteMetadata parseAndExtract(InputStream inputStream, String originalFilename) throws IOException {
        String text = parse(inputStream, originalFilename);
        log.info("开始 AI 结构化提取（文件：{}，文本长度：{}）", originalFilename, text.length());
        return extractService.extractV2(text);
    }
}
