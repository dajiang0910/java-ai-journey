package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.service.VectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量存储 REST 端点 —— 灌入文档 + 语义检索。
 * <p>
 * 这一层极薄：Controller 只做参数接收 + 调用 Service + 返回 ApiResponse。
 * 真正的向量化、存入、检索都在 VectorStoreService → VectorStore 里完成。
 */
@RestController
@RequestMapping("/api/vector")
@Tag(name = "向量存储", description = "文档灌入向量库 + 语义检索")
public class VectorController {

    private final VectorStoreService vectorStoreService;

    public VectorController(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 灌入一篇文档到向量库。
     * <p>
     * 请求体示例：
     * <pre>
     * {
     *   "content": "退款流程：用户在订单页面点击申请退款...",
     *   "metadata": {
     *     "source": "help-center",
     *     "title": "退款说明"
     *   }
     * }
     * </pre>
     */
    @PostMapping("/ingest")
    @Operation(summary = "灌入文档", description = "将文本内容向量化后存入 VectorStore")
    public ApiResponse<Map<String, Object>> ingest(@RequestBody IngestRequest request) {
        vectorStoreService.ingest(request.content(), request.metadata());
        return ApiResponse.success(Map.of(
                "status", "ingested",
                "contentPreview", request.content().substring(0, Math.min(50, request.content().length()))
        ));
    }

    /**
     * 语义检索 —— 用自然语言搜相关文档片段。
     * <p>
     * 请求体示例：
     * <pre>
     * {
     *   "query": "怎么退款",
     *   "topK": 3,
     *   "threshold": 0.6
     * }
     * </pre>
     */
    @PostMapping("/search")
    @Operation(summary = "语义检索", description = "用自然语言搜索最相关的文档片段")
    public ApiResponse<SearchResponse> search(
            @RequestBody SearchQueryRequest request) {
        List<Document> docs = vectorStoreService.search(
                request.query(), request.topK(), request.threshold());
        return ApiResponse.success(SearchResponse.from(docs));
    }

    // ---- DTO records ----

    public record IngestRequest(
            @Parameter(description = "文档文本内容", required = true)
            String content,
            @Parameter(description = "元数据（如 source、title 等）")
            Map<String, Object> metadata) {
        public IngestRequest {
            if (metadata == null) metadata = Map.of();
        }
    }

    public record SearchQueryRequest(
            @Parameter(description = "自然语言查询", required = true)
            String query,
            @Parameter(description = "返回前 K 条结果，默认 4")
            int topK,
            @Parameter(description = "最低相似度阈值 0.0~1.0，默认 0.7")
            double threshold) {
        public SearchQueryRequest {
            if (topK <= 0) topK = 4;
            if (threshold <= 0) threshold = 0.7;
        }
    }

    public record SearchResult(
            String id,
            String content,
            double similarity,
            Map<String, Object> metadata) {
    }

    public record SearchResponse(
            int hitCount,
            List<SearchResult> results) {
        static SearchResponse from(List<Document> docs) {
            List<SearchResult> results = docs.stream()
                    .map(doc -> new SearchResult(
                            doc.getId(),
                            doc.getText(),
                            doc.getScore() != null ? doc.getScore() : 0.0,
                            doc.getMetadata()))
                    .toList();
            return new SearchResponse(results.size(), results);
        }
    }
}
