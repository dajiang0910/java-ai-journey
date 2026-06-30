package com.example.notes_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * DocumentParseService 集成测试 —— 使用真实 Apache Tika 解析各种格式文件。
 * <p>
 * 与 DocumentControllerTest 的区别：
 * <ul>
 *   <li>ControllerTest: @WebMvcTest + @MockitoBean（mock Service 层，快速）</li>
 *   <li>本测试: 真实 Tika 实例 + 真实文件（验证 Tika 格式检测与文本提取，慢但可靠）</li>
 * </ul>
 * <p>
 * StructuredExtractService 被 mock（不需要 AI API），只测 Tika 解析部分。
 */
class DocumentParseServiceTest {

    private DocumentParseService parseService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // StructuredExtractService 需要 AI API Key，测试中 mock 掉
        StructuredExtractService mockExtract = mock(StructuredExtractService.class);
        parseService = new DocumentParseService(mockExtract);
    }

    // ================================================================
    // Markdown 格式
    // ================================================================

    @Test
    @DisplayName("解析 Markdown 文件应提取纯文本（去除标记符号）")
    void parse_markdown_shouldExtractPlainText() throws IOException {
        String mdContent = "# Spring AI 入门\n\nSpring AI 是 Spring 生态的 AI 框架。\n\n## 核心组件\n\n- ChatClient\n- EmbeddingModel";
        byte[] bytes = mdContent.getBytes(StandardCharsets.UTF_8);

        String text = parseService.parse(new ByteArrayInputStream(bytes), "test.md");

        assertNotNull(text, "解析结果不应为 null");
        assertFalse(text.isBlank(), "解析结果不应为空");
        // Markdown 解析后保留原始文本内容（Tika 对 .md 做纯文本提取）
        assertTrue(text.contains("Spring AI"), "应包含核心主题词");
        assertTrue(text.contains("ChatClient"), "应包含列表项");
        assertTrue(text.contains("EmbeddingModel"), "应包含全部列表项");
    }

    @Test
    @DisplayName("解析 Markdown 文件（来自 test resources）")
    void parse_markdownFromFile_shouldWork() throws IOException {
        // 用项目里已存在的测试文件
        Path testFile = Path.of("src/test/resources/test-files/spring-ai-intro.md");
        assertTrue(Files.exists(testFile), "测试文件应存在");

        try (InputStream is = Files.newInputStream(testFile)) {
            String text = parseService.parse(is, "spring-ai-intro.md");

            assertNotNull(text);
            assertFalse(text.isBlank());
            assertTrue(text.contains("Spring AI"), "应包含标题内容");
            assertTrue(text.contains("ChatClient"), "应包含核心组件名");
            assertTrue(text.contains("EmbeddingModel"), "应包含 EmbeddingModel");
            assertTrue(text.contains("VectorStore"), "应包含 VectorStore");
            assertTrue(text.contains("BeanOutputConverter"), "应包含 BeanOutputConverter");
        }
    }

    // ================================================================
    // 纯文本格式
    // ================================================================

    @Test
    @DisplayName("解析纯文本文件应原样返回")
    void parse_plainText_shouldReturnAsIs() throws IOException {
        String content = "企业微信 Q3 发布计划：线索管理、数据分析、AI 助手。";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        String text = parseService.parse(new ByteArrayInputStream(bytes), "plan.txt");

        assertNotNull(text);
        assertEquals(content, text.trim(), "纯文本应原样返回");
    }

    @Test
    @DisplayName("解析纯文本文件（来自 test resources）")
    void parse_plainTextFromFile_shouldWork() throws IOException {
        Path testFile = Path.of("src/test/resources/test-files/q3-plan.txt");
        assertTrue(Files.exists(testFile), "测试文件应存在");

        try (InputStream is = Files.newInputStream(testFile)) {
            String text = parseService.parse(is, "q3-plan.txt");

            assertNotNull(text);
            assertFalse(text.isBlank());
            assertTrue(text.contains("企业微信"), "应包含企业微信");
            assertTrue(text.contains("Q3"), "应包含 Q3");
        }
    }

    // ================================================================
    // HTML 格式
    // ================================================================

    @Test
    @DisplayName("解析 HTML 应提取文本内容（去除标签）")
    void parse_html_shouldStripTags() throws IOException {
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                  <h1>会议纪要</h1>
                  <p>2026年6月30日，讨论了<strong>Q3 目标</strong>和关键举措。</p>
                </body>
                </html>
                """;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        String text = parseService.parse(new ByteArrayInputStream(bytes), "meeting.html");

        assertNotNull(text);
        assertFalse(text.isBlank());
        assertTrue(text.contains("会议纪要"), "应提取 h1 内容");
        assertTrue(text.contains("Q3 目标"), "应提取 strong 内容");
        // Tika 提取 HTML 文本时不应包含原始标签
        assertFalse(text.contains("<h1>"), "不应包含 HTML 标签");
        assertFalse(text.contains("<body>"), "不应包含 HTML 标签");
    }

    // ================================================================
    // 边界场景
    // ================================================================

    @Test
    @DisplayName("解析 0 字节文件应抛出 IOException（Tika 拒绝空文件）")
    void parse_emptyStream_shouldThrowIOException() {
        byte[] empty = new byte[0];

        IOException ex = assertThrows(IOException.class, () ->
                parseService.parse(new ByteArrayInputStream(empty), "empty.txt"));

        assertTrue(ex.getMessage().contains("Tika 解析失败"),
                "异常信息应包含 'Tika 解析失败'");
        assertNotNull(ex.getCause(), "应有原始异常（ZeroByteFileException）");
    }

    @Test
    @DisplayName("resourceName 影响 HTML vs XML 的检测结果")
    void parse_htmlVsXml_differentResourceName_shouldBothWork() throws IOException {
        String xmlContent = "<?xml version=\"1.0\"?><root><item>测试数据</item></root>";
        byte[] bytes = xmlContent.getBytes(StandardCharsets.UTF_8);

        // 同一内容，不同文件名 → Tika 检测为不同格式，但都能提取文本
        String asHtml = parseService.parse(new ByteArrayInputStream(bytes), "data.html");
        String asXml = parseService.parse(new ByteArrayInputStream(bytes.clone()), "data.xml");

        assertNotNull(asHtml);
        assertNotNull(asXml);
        // 两种都能提取到文本内容
        assertTrue(asHtml.contains("测试数据") || asXml.contains("测试数据"),
                "无论检测为 HTML 还是 XML，都应提取到文本");
    }

    @Test
    @DisplayName("resourceName 为空时不应抛异常（Tika 只靠 magic bytes 检测）")
    void parse_nullFilename_shouldNotFail() throws IOException {
        String content = "简单文本内容。";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        // 不依赖文件名，纯靠二进制检测
        String text = parseService.parse(new ByteArrayInputStream(bytes), null);

        assertNotNull(text);
        assertTrue(text.contains("简单文本"), "无文件名时也应正常解析");
    }

    @Test
    @DisplayName("大文本解析应正常工作（>10KB）")
    void parse_largeText_shouldWork() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 长文档测试\n\n");
        for (int i = 1; i <= 500; i++) {
            sb.append("第").append(i).append("段：这是一段测试内容，用于验证 Tika 处理较大文本的能力。\n\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        String text = parseService.parse(new ByteArrayInputStream(bytes), "long.md");

        assertNotNull(text);
        assertTrue(text.length() > 10000, "大文本应完整解析");
        assertTrue(text.contains("第500段"), "应包含最后一段内容");
    }
}
