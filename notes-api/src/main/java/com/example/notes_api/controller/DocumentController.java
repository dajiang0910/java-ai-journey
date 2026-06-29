package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.DocumentParseResponse;
import com.example.notes_api.dto.NoteMetadata;
import com.example.notes_api.service.DocumentParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "文档解析与结构化提取")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

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
     * @param file 上传的文件（multipart/form-data，字段名 "file"）
     * @return 解析出的纯文本 + 元信息（文件名、MIME 类型、字符数）
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "文档文本解析",
            description = """
                    上传 PDF/Word/Markdown/HTML/TXT 等格式文件，
                    Tika 自动检测格式并提取纯文本内容。
                    返回文件名、检测到的 MIME 类型、文本长度和文本内容。
                    """
    )
    public ApiResponse<DocumentParseResponse> parse(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("收到文档解析请求：{}（大小：{} bytes）", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空，请上传有效文件");
        }

        String text = parseService.parse(file.getInputStream(), file.getOriginalFilename());

        var response = new DocumentParseResponse(
                file.getOriginalFilename(),
                "检测完成",  // MIME 类型在 service 层日志输出
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
     *
     * @param file 上传的文件（multipart/form-data，字段名 "file"）
     * @return 结构化元数据（标题/关键词/分类/难度/摘要）
     */
    @PostMapping(value = "/parse-and-extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "文档解析 + AI 元数据提取（完整链路）",
            description = """
                    Week 4 核心端点：上传文档 → Tika 解析为纯文本 →
                    Few-shot 增强的 AI 结构化提取 → 返回 NoteMetadata。

                    相比 v1 的 /api/extract/metadata 和 v2 的 /api/extract/metadata/v2，
                    本端点增加了文档解析层，可以直接接收文件而非手动粘贴文本。

                    五层防跑偏：@JsonProperty(required) → @JsonPropertyDescription →
                    Few-shot 范例 → System 角色约束 → 后校验兜底。
                    """
    )
    public ApiResponse<NoteMetadata> parseAndExtract(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("收到文档解析+提取请求：{}（大小：{} bytes）", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空，请上传有效文件");
        }

        NoteMetadata metadata = parseService.parseAndExtract(
                file.getInputStream(), file.getOriginalFilename());

        log.info("解析+提取完成：{} → title={}, category={}",
                file.getOriginalFilename(), metadata.title(), metadata.category());

        return ApiResponse.success(metadata);
    }
}
