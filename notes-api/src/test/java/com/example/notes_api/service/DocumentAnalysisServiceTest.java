package com.example.notes_api.service;

import com.example.notes_api.dto.TextStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DocumentAnalysisService 单元测试 —— 聚焦纯计算逻辑。
 * <p>
 * computeStatistics() 不依赖 AI，可以精确断言数值。
 * AI 相关方法（extractKeySentences、analyze）需要 API Key，在集成测试中验证。
 */
class DocumentAnalysisServiceTest {

    private DocumentAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        // 构造 Service 需要 ChatClient.Builder（mock 掉，computeStatistics 不依赖它）
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        ChatClient mockClient = mock(ChatClient.class);
        when(mockBuilder.build()).thenReturn(mockClient);
        StructuredExtractService mockExtract = mock(StructuredExtractService.class);
        analysisService = new DocumentAnalysisService(mockBuilder, mockExtract);
    }

    // ================================================================
    // computeStatistics —— 纯计算，无 AI 依赖
    // ================================================================

    @Test
    @DisplayName("空文本应返回全零统计")
    void computeStatistics_emptyText_shouldReturnZeros() {
        TextStatistics stats = analysisService.computeStatistics("");

        assertEquals(0, stats.totalChars());
        assertEquals(0, stats.totalWords());
        assertEquals(0, stats.totalSentences());
        assertEquals(0, stats.totalParagraphs());
        assertEquals(0, stats.estimatedMinutes());
    }

    @Test
    @DisplayName("null 文本应返回全零统计")
    void computeStatistics_nullText_shouldReturnZeros() {
        TextStatistics stats = analysisService.computeStatistics(null);

        assertEquals(0, stats.totalChars());
        assertEquals(0, stats.totalWords());
        assertEquals(0, stats.totalSentences());
        assertEquals(0, stats.totalParagraphs());
        assertEquals(0, stats.estimatedMinutes());
    }

    @Test
    @DisplayName("纯中文文本：字符数、句子数、段落数应准确计算")
    void computeStatistics_chineseText_shouldCountCorrectly() {
        String text = """
                企业微信 Q3 发布计划。

                7 月上线线索管理功能。8 月上线数据分析仪表盘。9 月全面接入 AI 助手。
                """;

        TextStatistics stats = analysisService.computeStatistics(text);

        assertTrue(stats.totalChars() > 30, "应有 30+ 字符");
        assertTrue(stats.totalWords() > 20, "应有 20+ 词");
        assertEquals(4, stats.totalSentences(), "4 个句子（按句号分隔）");
        assertEquals(2, stats.totalParagraphs(), "2 个段落（空行分隔）");
        assertTrue(stats.estimatedMinutes() >= 1, "阅读时间至少 1 分钟");
    }

    @Test
    @DisplayName("中英混合文本：中文按字数、英文按单词数估算")
    void computeStatistics_mixedLanguage_shouldCountBoth() {
        String text = "Spring AI 是 Spring 生态的 AI 框架。它支持 ChatClient 和 EmbeddingModel。";

        TextStatistics stats = analysisService.computeStatistics(text);

        assertTrue(stats.totalWords() >= 10, "中英混合应有 10+ 词");
        assertEquals(2, stats.totalSentences(), "2 句");
    }

    @Test
    @DisplayName("单句短文：阅读时间应至少 1 分钟")
    void computeStatistics_shortText_shouldBeAtLeastOneMinute() {
        String text = "你好世界。";

        TextStatistics stats = analysisService.computeStatistics(text);

        assertEquals(1, stats.estimatedMinutes(), "即使只有几个字，也至少 1 分钟");
        assertEquals(1, stats.totalSentences());
    }

    @Test
    @DisplayName("英文文本：单词数应按空格分词统计")
    void computeStatistics_englishText_shouldCountWords() {
        String text = "Spring AI is a framework for building AI applications. It provides ChatClient and EmbeddingModel.";

        TextStatistics stats = analysisService.computeStatistics(text);

        assertTrue(stats.totalWords() >= 12, "英文应有 12+ 单词");
        assertEquals(2, stats.totalSentences());
    }
}
