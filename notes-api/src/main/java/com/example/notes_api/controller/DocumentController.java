package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.DocumentParseResponse;
import com.example.notes_api.dto.NoteMetadata;
import com.example.notes_api.service.DocumentParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
// 注意：Swagger 的 @ApiResponse/@ApiResponses 用完全限定名，
// 因为项目 DTO 也叫 ApiResponse，会有编译冲突
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档解析接口 —— 知识库"接入层"入口。
 * <p>
 * 核心链路：
 * <pre>
 * MultipartFile → Tika 解析 → 纯文本 → (可选) AI 结构化提取 → NoteMetadata
 * </pre>
 * 两个端点分别对应管道的两段：纯解析 / 解析+提取一键完成。
 * <p>
 * <b>文件限制</b>：最大 10MB，支持格式 PDF/Word (docx)/Markdown/HTML/TXT/PPT 等。
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "文档解析与结构化提取（知识库接入层）")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    /** 单文件最大大小（与 application.properties 中 multipart.max-file-size 保持一致） */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final DocumentParseService parseService;

    public DocumentController(DocumentParseService parseService) {
        this.parseService = parseService;
    }

    /**
     * 上传文档 → 返回纯文本。
     * <p>
     * 适用场景：只需要文档文本，后续处理（比如摘要、翻译）在调用方自己组合。
     * <p>
     * 支持格式：PDF、Word (docx)、Markdown、HTML、TXT、PPT 等（Tika 自动检测）。
     *
     * @param file 上传的文件（multipart/form-data，字段名 "file"，最大 10 MB）
     * @return 解析出的纯文本 + 元信息（文件名、MIME 类型、字符数）
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "文档文本解析",
            description = """
                    上传 PDF/Word/Markdown/HTML/TXT 等格式文件，
                    Tika 自动检测格式并提取纯文本内容。
                    返回文件名、检测到的 MIME 类型、文本长度和文本内容。
                    """,
            operationId = "parseDocument"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "解析成功",
                    content = @Content(schema = @Schema(implementation = DocumentParseResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "文件为空",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":400,\"message\":\"文件为空，请上传有效文件\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "文件大小超限（>10 MB）",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":413,\"message\":\"文件大小超过上限（10 MB），请压缩后重试\"}")))
    })
    public ApiResponse<DocumentParseResponse> parse(
            @Parameter(description = "要解析的文档文件（PDF/Word/Markdown/HTML/TXT 等），最大 10 MB",
                    required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("收到文档解析请求：{}（大小：{} bytes）", file.getOriginalFilename(), file.getSize());

        // L1 前置校验：空文件
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空，请上传有效文件");
        }

        // L2 前置校验：文件大小（双重保险，与 Spring multipart 配置互补）
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(413,
                    String.format("文件大小超过上限（%d MB），请压缩后重试", MAX_FILE_SIZE / 1024 / 1024));
        }

        String text = parseService.parse(file.getInputStream(), file.getOriginalFilename());

        // 从 Service 的日志中获取的 MIME 类型无法在此处拿到，用标记代替
        // （真实场景可用 ThreadLocal 或返回自定义对象携带 MIME type）
        var response = new DocumentParseResponse(
                file.getOriginalFilename(),
                "解析成功（详见日志）",
                text.length(),
                text
        );

        return ApiResponse.success(response);
    }

    /**
     * 上传文档 → 解析 → AI 提取结构化元数据（完整链路一步到位）。
     * <p>
     * 这是 Week 4 的核心产出端点——知识库"接入层"的完整闭环：
     * <pre>
     * 1. Tika 解析文档 → 纯文本
     * 2. extractV2()（Few-shot + Schema + 后校验）→ NoteMetadata
     * </pre>
     * 相比单独调用 /api/extract/metadata/v2，这个端点省去了"先手动把文本粘贴到请求体"的步骤。
     * <p>
     * <b>五层防跑偏</b>：@JsonProperty(required) → @JsonPropertyDescription →
     * Few-shot 范例 → System 角色约束 → 后校验兜底
     *
     * @param file 上传的文件（multipart/form-data，字段名 "file"，最大 10 MB）
     * @return 结构化元数据（标题/关键词/分类/难度/摘要）
     */
    @PostMapping(value = "/parse-and-extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "文档解析 + AI 元数据提取（完整链路）",
            description = """
                    <h3>Week 4 核心端点：上传文档 → Tika 解析 → AI 结构化提取</h3>
                    <ul>
                      <li><b>Tika 解析</b>：自动检测格式（magic bytes），提取纯文本</li>
                      <li><b>AI 提取</b>：Few-shot 增强的 extractV2()，返回类型安全的 NoteMetadata</li>
                    </ul>
                    <p>五层防跑偏：@JsonProperty(required) → @JsonPropertyDescription →
                    Few-shot 范例 → System 角色约束 → 后校验兜底。</p>
                    <p>支持格式：PDF / Word (docx) / Markdown / HTML / TXT / PPT / Excel 等。</p>
                    """,
            operationId = "parseAndExtractDocument"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "提取成功",
                    content = @Content(schema = @Schema(implementation = NoteMetadata.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "文件为空",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":400,\"message\":\"文件为空，请上传有效文件\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "文件大小超限（>10 MB）",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":413,\"message\":\"文件大小超过上限（10 MB），请压缩后重试\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "解析失败（如文件损坏）",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":500,\"message\":\"Tika 解析失败：bad-file.pdf\"}")))
    })
    public ApiResponse<NoteMetadata> parseAndExtract(
            @Parameter(description = "要解析的文档文件（PDF/Word/Markdown/HTML/TXT 等），最大 10 MB",
                    required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("收到文档解析+提取请求：{}（大小：{} bytes）", file.getOriginalFilename(), file.getSize());

        // L1 前置校验：空文件
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空，请上传有效文件");
        }

        // L2 前置校验：文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(413,
                    String.format("文件大小超过上限（%d MB），请压缩后重试", MAX_FILE_SIZE / 1024 / 1024));
        }

        NoteMetadata metadata = parseService.parseAndExtract(
                file.getInputStream(), file.getOriginalFilename());

        log.info("解析+提取完成：{} → title={}, category={}",
                file.getOriginalFilename(), metadata.title(), metadata.category());

        return ApiResponse.success(metadata);
    }
}
