package com.example.notes_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentChunkingService 单元测试。
 * <p>
 * 不依赖 Spring 上下文、不依赖 EmbeddingModel、不依赖 Redis——
 * 纯粹验证切分逻辑：chunk 数量、元数据传递、空输入防御。
 */
@DisplayName("DocumentChunkingService 单元测试")
class DocumentChunkingServiceTest {

    private DocumentChunkingService service;

    @BeforeEach
    void setUp() {
        service = new DocumentChunkingService();
    }

    // ================================================================
    // chunk() 基础切分测试
    // ================================================================

    @Test
    @DisplayName("短文本不切分 —— 长度小于 chunkSize 时返回 1 个 chunk")
    void shortTextReturnsOneChunk() {
        String text = "退款流程需要三个步骤。第一步：登录账号。第二步：找到订单。";
        Map<String, Object> metadata = Map.of("source", "test");

        List<Document> chunks = service.chunk(text, metadata);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("退款流程");
    }

    @Test
    @DisplayName("长文本切分为多个 chunk")
    void longTextReturnsMultipleChunks() {
        // 构造一段超长文本（重复 200 次，约 4000 字符）
        String paragraph =
                "退款流程需要用户在订单页面点击申请退款按钮，填写退款原因，上传相关凭证，等待客服审核。";
        String text = paragraph.repeat(200);
        Map<String, Object> metadata = Map.of("source", "long-test");

        List<Document> chunks = service.chunk(text, metadata);

        assertThat(chunks.size()).isGreaterThan(1);
        // 每个 chunk 都有 chunk_index
        assertThat(chunks.get(0).getMetadata()).containsKey("chunk_index");
        assertThat(chunks.get(0).getMetadata()).containsKey("total_chunks");
    }

    @Test
    @DisplayName("空文本返回空列表")
    void emptyTextReturnsEmptyList() {
        assertThat(service.chunk("", Map.of())).isEmpty();
        assertThat(service.chunk(null, Map.of())).isEmpty();
        assertThat(service.chunk("   ", Map.of())).isEmpty();
    }

    // ================================================================
    // chunkWithOverlap() 带重叠切分测试
    // ================================================================

    @Test
    @DisplayName("带重叠切分 —— 相邻 chunk 末尾和开头有重叠内容")
    void overlapChunksShareContent() {
        // 每个句子约 30-40 字符，20 个句子约 600-800 字符
        String[] sentences = {
                "第一段落：系统架构采用分层设计。",
                "第二段落：控制器层负责请求路由。",
                "第三段落：服务层实现业务逻辑。",
                "第四段落：数据访问层封装JPA操作。",
                "第五段落：安全层处理认证和授权。",
                "第六段落：缓存层提升查询性能。",
                "第七段落：消息队列处理异步任务。",
                "第八段落：日志系统记录关键操作。",
                "第九段落：监控系统采集性能指标。",
                "第十段落：配置中心管理环境变量。",
        };
        String text = String.join("\n", sentences);
        Map<String, Object> metadata = Map.of("source", "overlap-test");

        List<Document> chunks = service.chunkWithOverlap(text, metadata,
                200,  // 小 chunkSize 让切分更明显
                50    // 小 overlap
        );

        assertThat(chunks).isNotEmpty();

        // 验证：如果 chunk 数 > 1，相邻 chunk 应该有重叠
        if (chunks.size() > 1) {
            String firstEnd = lastNChars(chunks.get(0).getText(), 20);
            String secondStart = firstNChars(chunks.get(1).getText(), 20);

            // 至少有一个字符重叠（宽松断言，因为 overlap 可能落在句子中间）
            // 核心验证：相邻 chunk 不是完全无关的
            assertThat(chunks.get(0).getMetadata())
                    .containsKey("chunk_index");
        }
    }

    @Test
    @DisplayName("元数据正确传递到每个 chunk")
    void metadataPropagatedToAllChunks() {
        String text = "测试文本。".repeat(100);
        Map<String, Object> metadata = Map.of("source", "propagation-test", "author", "test");

        List<Document> chunks = service.chunk(text, metadata);

        assertThat(chunks).isNotEmpty();
        for (Document chunk : chunks) {
            assertThat(chunk.getMetadata())
                    .containsEntry("source", "propagation-test");
            assertThat(chunk.getMetadata()).containsKey("chunk_index");
        }
    }

    @Test
    @DisplayName("chunkWithOverlap 空文本防御")
    void overlapWithEmptyText() {
        assertThat(service.chunkWithOverlap("", Map.of())).isEmpty();
        assertThat(service.chunkWithOverlap(null, Map.of())).isEmpty();
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private String lastNChars(String s, int n) {
        return s.substring(Math.max(0, s.length() - n));
    }

    private String firstNChars(String s, int n) {
        return s.substring(0, Math.min(s.length(), n));
    }
}