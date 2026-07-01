package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.service.DocumentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文档灌库 REST 端点 —— 知识库"接入层"的 HTTP 入口。
 * <p>
 * 提供两种灌库方式：
 * <ol>
 *   <li><b>POST /api/ingestion/upload</b>：上传文件（PDF/Word/Markdown 等），自动解析 + 切分 + 入库</li>
 *   <li><b>POST /api/ingestion/text</b>：直接提交纯文本，跳过文件解析</li>
 * </ol>
 * <p>
 * 这一层极薄：只做参数校验 + 调 Service + 返回结果。
 * Controller 不懂 Tika、不懂 TokenTextSplitter、不懂 VectorStore ——
 * 这就是三层架构的价值：每层只干自己的活。
 */
@RestController
@RequestMapping("/api/ingestion")
@Tag(name = "文档灌库", description = "上传文件或文本 → 自动解析/切分/向量化 → 写入知识库")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final DocumentIngestionService ingestionService;

    public IngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 上传文件并灌入向量库。
     * <p>
     * 支持格式：PDF、Word (docx)、Markdown、HTML、TXT、PPT 等（Tika 自动检测）。
     * <p>
     * 用 curl 测试：
     * <pre>
     * curl -X POST http://localhost:8080/api/ingestion/upload \
     *   -F "file=@退款政策.pdf"
     * </pre>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件灌库",
            description = "上传 PDF/Word/Markdown 等文件，自动解析 + 切分 + 向量化 + 入库")
    public ApiResponse<Map<String, Object>> upload(
            @Parameter(description = "要上传的文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空，请选择一个文件上传");
        }

        log.info("收到文件上传: {} ({} KB)", file.getOriginalFilename(),
                file.getSize() / 1024);

        DocumentIngestionService.IngestionResult result =
                ingestionService.ingestFile(file.getInputStream(),
                        file.getOriginalFilename());

        return ApiResponse.success(Map.of(
                "source", result.source(),
                "originalChars", result.originalChars(),
                "chunkCount", result.chunkCount(),
                "tookMs", result.tookMs(),
                "message", "文件已成功灌入知识库，共 " + result.chunkCount() + " 个片段"
        ));
    }

    /**
     * 直接提交纯文本灌库（跳过文件解析）。
     * <p>
     * 用 curl 测试：
     * <pre>
     * curl -X POST http://localhost:8080/api/ingestion/text \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "退款流程：用户在订单页面点击申请退款...", "source": "manual-test"}'
     * </pre>
     */
    @PostMapping("/text")
    @Operation(summary = "纯文本灌库",
            description = "直接提交文本内容，跳过文件解析，直接切分 + 向量化 + 入库")
    public ApiResponse<Map<String, Object>> text(
            @RequestBody TextIngestRequest request) {

        if (request.text() == null || request.text().isBlank()) {
            return ApiResponse.error(400, "文本内容不能为空");
        }

        DocumentIngestionService.IngestionResult result =
                ingestionService.ingestText(request.text(), request.source());

        return ApiResponse.success(Map.of(
                "source", result.source(),
                "originalChars", result.originalChars(),
                "chunkCount", result.chunkCount(),
                "tookMs", result.tookMs(),
                "message", "文本已成功灌入知识库，共 " + result.chunkCount() + " 个片段"
        ));
    }

    // ---- DTO ----

    @Schema(description = "纯文本灌库请求")
    public record TextIngestRequest(
            @Schema(description = "文本内容", example = "退款流程：用户在订单页面点击...")
            String text,
            @Schema(description = "来源标识", example = "help-center-import")
            String source) {
        public TextIngestRequest {
            if (source == null || source.isBlank()) {
                source = "api-ingest-" + System.currentTimeMillis();
            }
        }
    }
}