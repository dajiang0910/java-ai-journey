package com.example.notes_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档灌库管线 —— RAG "接入层"的第二个半环。
 * <p>
 * 职责：编排 解析 → 切分 → 批量入库 三步。
 * <p>
 * 管线全景：
 * <pre>
 * 文件上传 → DocumentParseService.parse() → 纯文本
 *          → DocumentChunkingService.chunkWithOverlap() → List&lt;Document&gt;
 *          → VectorStoreService.ingestBatch() → Redis Stack
 *          → 返回 IngestionResult
 * </pre>
 * <p>
 * 设计原则：Service 层只做编排，不写具体逻辑（parse/chunk/ingest 都在各自 Service 里）。
 * 这就是"组合优于继承"在服务层的体现。
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentParseService parseService;
    private final DocumentChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;

    public DocumentIngestionService(DocumentParseService parseService,
                                    DocumentChunkingService chunkingService,
                                    VectorStoreService vectorStoreService) {
        this.parseService = parseService;
        this.chunkingService = chunkingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 灌库结果。
     */
    public record IngestionResult(
            String source,          // 来源文件名
            int originalChars,      // 原始文本字符数
            int chunkCount,         // 切分后 chunk 数量
            long tookMs             // 耗时（毫秒）
    ) {}

    // ================================================================
    // 公开 API
    // ================================================================

    /**
     * 从文件灌库 —— 完整三步管线。
     * <p>
     * 这是知识库"接入层"的核心入口：
     * <ol>
     *   <li>Tika 解析：PDF/Word/Markdown → 纯文本</li>
     *   <li>TokenTextSplitter 切分：长文本 → 语义片段（带重叠）</li>
     *   <li>VectorStore 批量入库：片段 → Embedding → Redis Stack</li>
     * </ol>
     *
     * @param inputStream 文件输入流
     * @param filename    原始文件名（用于日志和元数据）
     * @return 灌库结果（chunk 数 + 耗时）
     * @throws IOException 文件解析失败时抛出
     */
    public IngestionResult ingestFile(InputStream inputStream, String filename)
            throws IOException {
        long start = System.currentTimeMillis();

        // Step 1: 解析文档为纯文本
        String text = parseService.parse(inputStream, filename);
        log.info("[管线 Step 1/3] Tika 解析完成: {}, {} 字符", filename, text.length());

        // Step 2: 切分（带重叠）
        Map<String, Object> metadata = buildBaseMetadata(filename);
        List<Document> chunks = chunkingService.chunkWithOverlap(text, metadata);
        log.info("[管线 Step 2/3] 切分完成: {} → {} 个 chunk", filename, chunks.size());

        // Step 3: 批量灌入向量库
        vectorStoreService.ingestBatch(chunks);
        long tookMs = System.currentTimeMillis() - start;
        log.info("[管线 Step 3/3] 入库完成: {} 个 chunk 已写入 Redis Stack, 耗时 {}ms",
                chunks.size(), tookMs);

        return new IngestionResult(filename, text.length(), chunks.size(), tookMs);
    }

    /**
     * 从纯文本灌库 —— 跳过 Tika 解析，直接切分入库。
     * <p>
     * 适用于：API 直接传入文本（非文件上传）、测试数据快速灌入。
     *
     * @param text     待灌入的文本
     * @param source   来源标识（如 "manual-import"、API 端点名）
     * @return 灌库结果
     */
    public IngestionResult ingestText(String text, String source) {
        long start = System.currentTimeMillis();

        Map<String, Object> metadata = buildBaseMetadata(source);
        List<Document> chunks = chunkingService.chunkWithOverlap(text, metadata);
        log.info("[管线] 切分完成: {} → {} 个 chunk", source, chunks.size());

        vectorStoreService.ingestBatch(chunks);
        long tookMs = System.currentTimeMillis() - start;
        log.info("[管线] 入库完成: {} 个 chunk → Redis Stack, 耗时 {}ms",
                chunks.size(), tookMs);

        return new IngestionResult(source, text.length(), chunks.size(), tookMs);
    }

    // ================================================================
    // 内部工具
    // ================================================================

    /**
     * 构建灌库用的基础元数据。
     * <p>
     * 这些元数据会传递到每个 chunk 的 metadata 里，
     * 后续检索时可用于过滤（如 SearchRequest.filterExpression("source == 'refund-policy.pdf'"））。
     */
    private Map<String, Object> buildBaseMetadata(String source) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", source);
        meta.put("ingested_at", Instant.now().toString());
        return meta;
    }
}