package com.example.notes_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量存储服务 —— 封装 VectorStore 接口。
 * <p>
 * VectorStore = 向量数据库的 JDBC。不管底层是 Redis Stack、PGVector 还是 Milvus，
 * 业务代码只依赖这个接口。要换向量库？只改 pom.xml + application.properties，这里一行不动。
 * <p>
 * 你之前用 JdbcTemplate 操作数据库——VectorStore 就是向量版的 JdbcTemplate。
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        log.info("VectorStore 实现: {}", vectorStore.getClass().getSimpleName());
    }

    /**
     * 灌入单条文档（内容 + 元数据）。
     * <p>
     * 背后发生的事：
     * 1. Spring AI 自动调 EmbeddingModel 把 content 转成 1536 维向量
     * 2. 向量 + 元数据一起写入 VectorStore
     *
     * @param content  文档文本内容
     * @param metadata 元数据（如文件名、来源、页数等）
     */
    public void ingest(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
        log.info("文档已入库: id={}, metadata={}",
                doc.getId(), metadata);
    }

    /**
     * 批量灌入文档（Day 3 灌库管线会用到）。
     */
    public void ingestBatch(List<Document> documents) {
        vectorStore.add(documents);
        log.info("批量入库完成: {} 篇文档", documents.size());
    }

    /**
     * 语义检索 —— 用自然语言搜最相关的文档片段。
     * <p>
     * 背后发生的事：
     * 1. Spring AI 自动调 EmbeddingModel 把 query 转成向量
     * 2. VectorStore 做最近邻搜索（HNSW 算法，O(log N)）
     * 3. 返回 topK 条最相似的文档
     *
     * @param query     自然语言查询（如 "如何退款"）
     * @param topK      返回前 K 条结果
     * @param threshold 最低相似度阈值（0.0~1.0），低于此值的结果丢弃
     * @return 按相似度降序排列的文档列表
     */
    public List<Document> search(String query, int topK, double threshold) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build()
        );
        log.info("检索完成: query='{}', topK={}, threshold={}, 命中 {} 条",
                query, topK, threshold, results.size());
        return results;
    }

    /**
     * 检索（使用默认 topK=4, threshold=0.7）。
     */
    public List<Document> search(String query) {
        return search(query, 4, 0.7);
    }

    /**
     * 按 ID 删除文档。
     */
    public void delete(List<String> ids) {
        vectorStore.delete(ids);
        log.info("已删除 {} 条文档: {}", ids.size(), ids);
    }

    /**
     * 暴露底层 VectorStore（给 Controller 直接调用，演示用）。
     */
    public VectorStore getVectorStore() {
        return vectorStore;
    }
}
