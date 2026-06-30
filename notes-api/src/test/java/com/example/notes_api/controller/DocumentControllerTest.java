package com.example.notes_api.controller;

import com.example.notes_api.dto.DocumentAnalysisResponse;
import com.example.notes_api.dto.NoteMetadata;
import com.example.notes_api.dto.TextStatistics;
import com.example.notes_api.service.DocumentAnalysisService;
import com.example.notes_api.service.DocumentParseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DocumentController 控制器测试 —— Week 4 Day 3-5。
 * <p>
 * 覆盖三个端点：
 * <ul>
 *   <li>POST /api/documents/parse —— 上传文档 → 返回纯文本</li>
 *   <li>POST /api/documents/parse-and-extract —— 上传文档 → 解析 → AI 提取 → NoteMetadata</li>
 *   <li>POST /api/documents/analyze —— 上传文档 → 智能分析 → DocumentAnalysisResponse（Day 5 新增）</li>
 * </ul>
 * 使用 @WebMvcTest 只加载 Controller 层，@MockitoBean 模拟 Service 层。
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DocumentParseService parseService;

    @MockitoBean
    DocumentAnalysisService analysisService;

    // ================================================================
    // POST /api/documents/parse —— 文档文本解析
    // ================================================================

    @Test
    @DisplayName("POST /api/documents/parse 上传 Markdown 文件应返回解析文本")
    void parse_shouldReturnDocumentParseResponse() throws Exception {
        String fileContent = "# Spring AI 入门\nSpring AI 是 Spring 生态的 AI 框架。";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spring-ai-intro.md",
                "text/markdown",
                fileContent.getBytes()
        );

        when(parseService.parse(any(), eq("spring-ai-intro.md")))
                .thenReturn(fileContent);

        mockMvc.perform(multipart("/api/documents/parse")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.filename").value("spring-ai-intro.md"))
                .andExpect(jsonPath("$.data.textLength").value(fileContent.length()))
                .andExpect(jsonPath("$.data.text").value(fileContent));
    }

    @Test
    @DisplayName("POST /api/documents/parse 上传空文件应返回 400")
    void parse_withEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.md",
                "text/markdown",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/parse")
                        .file(emptyFile))
                .andExpect(status().isOk())   // 业务层 400 通过 ApiResponse 返回，HTTP 仍 200
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件为空，请上传有效文件"));
    }

    // ================================================================
    // POST /api/documents/parse-and-extract —— 完整链路
    // ================================================================

    @Test
    @DisplayName("POST /api/documents/parse-and-extract 上传文档应返回 AI 提取的 NoteMetadata")
    void parseAndExtract_shouldReturnNoteMetadata() throws Exception {
        String fileContent = "企业微信 Q3 发布计划：7 月线索管理，8 月数据分析，9 月 AI 助手。";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "q3-plan.txt",
                "text/plain",
                fileContent.getBytes()
        );

        NoteMetadata mockMeta = new NoteMetadata(
                "企业微信 Q3 发布计划",
                List.of("企业微信", "Q3计划", "线索管理", "数据分析", "AI助手"),
                "产品",
                "中级",
                "2026年Q3企业微信将上线线索管理、数据仪表盘和AI助手"
        );
        when(parseService.parseAndExtract(any(), eq("q3-plan.txt")))
                .thenReturn(mockMeta);

        mockMvc.perform(multipart("/api/documents/parse-and-extract")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.title").value("企业微信 Q3 发布计划"))
                .andExpect(jsonPath("$.data.keywords[0]").value("企业微信"))
                .andExpect(jsonPath("$.data.keywords[1]").value("Q3计划"))
                .andExpect(jsonPath("$.data.keywords[2]").value("线索管理"))
                .andExpect(jsonPath("$.data.keywords[3]").value("数据分析"))
                .andExpect(jsonPath("$.data.keywords[4]").value("AI助手"))
                .andExpect(jsonPath("$.data.category").value("产品"))
                .andExpect(jsonPath("$.data.difficulty").value("中级"))
                .andExpect(jsonPath("$.data.summary").value("2026年Q3企业微信将上线线索管理、数据仪表盘和AI助手"));
    }

    @Test
    @DisplayName("POST /api/documents/parse-and-extract 上传空文件应返回 400")
    void parseAndExtract_withEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/parse-and-extract")
                        .file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件为空，请上传有效文件"));
    }

    // ================================================================
    // POST /api/documents/analyze —— 智能分析（Day 5 新增）
    // ================================================================

    @Test
    @DisplayName("POST /api/documents/analyze 上传文档应返回综合分析结果")
    void analyze_shouldReturnDocumentAnalysisResponse() throws Exception {
        String fileContent = "# Spring AI 框架入门\nSpring AI 是 Spring 生态的 AI 框架。\n\n## 核心组件\n- ChatClient\n- EmbeddingModel\n- VectorStore";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spring-ai-intro.md",
                "text/markdown",
                fileContent.getBytes()
        );

        // Mock 解析结果
        DocumentParseService.ParseResult parseResult =
                new DocumentParseService.ParseResult(fileContent, "text/markdown");
        when(parseService.parseWithMetadata(any(), eq("spring-ai-intro.md")))
                .thenReturn(parseResult);

        // Mock 分析结果
        NoteMetadata meta = new NoteMetadata("Spring AI 框架介绍",
                List.of("Spring AI", "ChatClient", "EmbeddingModel"),
                "技术", "入门", "Spring AI 让 Java 开发者零门槛上手 LLM 应用开发");
        TextStatistics stats = new TextStatistics(126, 46, 2, 2, 1);
        List<String> keySentences = List.of(
                "Spring AI 是 Spring 生态的 AI 框架",
                "ChatClient 是统一的对话客户端"
        );
        DocumentAnalysisResponse analysis = new DocumentAnalysisResponse(
                "spring-ai-intro.md", "text/markdown", meta, stats, keySentences, "约 1 分钟");
        when(analysisService.analyze(eq(fileContent), eq("spring-ai-intro.md"), eq("text/markdown")))
                .thenReturn(analysis);

        mockMvc.perform(multipart("/api/documents/analyze")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.filename").value("spring-ai-intro.md"))
                .andExpect(jsonPath("$.data.detectedType").value("text/markdown"))
                .andExpect(jsonPath("$.data.metadata.title").value("Spring AI 框架介绍"))
                .andExpect(jsonPath("$.data.metadata.category").value("技术"))
                .andExpect(jsonPath("$.data.metadata.difficulty").value("入门"))
                .andExpect(jsonPath("$.data.statistics.totalChars").value(126))
                .andExpect(jsonPath("$.data.statistics.totalSentences").value(2))
                .andExpect(jsonPath("$.data.statistics.estimatedMinutes").value(1))
                .andExpect(jsonPath("$.data.keySentences[0]").value("Spring AI 是 Spring 生态的 AI 框架"))
                .andExpect(jsonPath("$.data.keySentences[1]").value("ChatClient 是统一的对话客户端"))
                .andExpect(jsonPath("$.data.readingTime").value("约 1 分钟"));
    }

    @Test
    @DisplayName("POST /api/documents/analyze 上传空文件应返回 400")
    void analyze_withEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.md",
                "text/markdown",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/analyze")
                        .file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件为空，请上传有效文件"));
    }
}
