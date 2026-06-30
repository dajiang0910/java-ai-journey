package com.example.notes_api.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MetricsAdvisor 单元测试。
 * <p>
 * 使用 SimpleMeterRegistry（Micrometer 的内存实现）替代真实 Prometheus，
 * 验证 Advisor 能正确采集耗时和 Token 指标。
 */
class MetricsAdvisorTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricsAdvisor advisor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        advisor = new MetricsAdvisor(meterRegistry, "qwen-turbo");
    }

    @Test
    @DisplayName("应正确上报调用次数（Counter）")
    void shouldIncrementCallCount() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = createMockResponse(100, 50, 150);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        advisor.adviseCall(request, chain);

        Counter counter = meterRegistry.find("llm.call.count")
                .tag("model", "qwen-turbo")
                .counter();
        assertNotNull(counter, "调用次数 Counter 应存在");
        assertEquals(1.0, counter.count(), 0.01, "调用次数应为 1");
    }

    @Test
    @DisplayName("应正确上报调用耗时（Timer）")
    void shouldRecordDuration() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = createMockResponse(100, 50, 150);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        advisor.adviseCall(request, chain);

        Timer timer = meterRegistry.find("llm.call.duration")
                .tag("model", "qwen-turbo")
                .timer();
        assertNotNull(timer, "调用耗时 Timer 应存在");
        assertTrue(timer.count() > 0, "应至少记录了一次调用耗时");
    }

    @Test
    @DisplayName("应正确上报 Token 用量（prompt + completion + total）")
    void shouldRecordTokenUsage() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = createMockResponse(200, 80, 280);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        advisor.adviseCall(request, chain);

        // Prompt Token
        Counter promptTokens = meterRegistry.find("llm.call.tokens")
                .tag("model", "qwen-turbo")
                .tag("type", "prompt")
                .counter();
        assertNotNull(promptTokens, "Prompt Token Counter 应存在");
        assertEquals(200.0, promptTokens.count(), 0.01);

        // Completion Token
        Counter completionTokens = meterRegistry.find("llm.call.tokens")
                .tag("model", "qwen-turbo")
                .tag("type", "completion")
                .counter();
        assertNotNull(completionTokens, "Completion Token Counter 应存在");
        assertEquals(80.0, completionTokens.count(), 0.01);

        // Total Token
        Counter totalTokens = meterRegistry.find("llm.call.tokens")
                .tag("model", "qwen-turbo")
                .tag("type", "total")
                .counter();
        assertNotNull(totalTokens, "Total Token Counter 应存在");
        assertEquals(280.0, totalTokens.count(), 0.01);
    }

    @Test
    @DisplayName("Token 用量为 null 时不应抛异常（防御性编程）")
    void shouldHandleNullTokenUsage() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = createMockResponseWithoutUsage();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        // 不应抛出异常
        assertDoesNotThrow(() -> advisor.adviseCall(request, chain));

        // 调用次数仍然应上报
        Counter count = meterRegistry.find("llm.call.count")
                .tag("model", "qwen-turbo")
                .counter();
        assertNotNull(count);
    }

    @Test
    @DisplayName("多次调用应正确累加指标")
    void shouldAccumulateMetricsAcrossCalls() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        // 注意：必须在 when() 之前创建 mock 响应，
        // 否则在 thenReturn() 中调用 createMockResponse 会导致 Mockito UnfinishedStubbing
        ChatClientResponse resp1 = createMockResponse(100, 50, 150);
        ChatClientResponse resp2 = createMockResponse(120, 60, 180);
        ChatClientResponse resp3 = createMockResponse(80, 40, 120);
        when(chain.nextCall(request)).thenReturn(resp1, resp2, resp3);

        advisor.adviseCall(request, chain);
        advisor.adviseCall(request, chain);
        advisor.adviseCall(request, chain);

        // 调用次数应累加
        Counter count = meterRegistry.find("llm.call.count")
                .tag("model", "qwen-turbo")
                .counter();
        assertEquals(3.0, count.count(), 0.01);

        // Prompt Token 应累加：100 + 120 + 80 = 300
        Counter promptTokens = meterRegistry.find("llm.call.tokens")
                .tag("type", "prompt")
                .counter();
        assertEquals(300.0, promptTokens.count(), 0.01);

        // Completion Token 应累加：50 + 60 + 40 = 150
        Counter completionTokens = meterRegistry.find("llm.call.tokens")
                .tag("type", "completion")
                .counter();
        assertEquals(150.0, completionTokens.count(), 0.01);
    }

    @Test
    @DisplayName("getName 应返回 metricsAdvisor")
    void getNameShouldReturnCorrectName() {
        assertEquals("metricsAdvisor", advisor.getName());
    }

    @Test
    @DisplayName("getOrder 应返回 HIGHEST_PRECEDENCE")
    void getOrderShouldReturnHighestPrecedence() {
        assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE,
                advisor.getOrder());
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /** 创建一个带有 Usage 信息的 mock ChatClientResponse */
    private ChatClientResponse createMockResponse(int promptTokens, int completionTokens, int totalTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getTotalTokens()).thenReturn(totalTokens);

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        // ChatClientResponse(ChatResponse, Map<String, Object>)
        return new ChatClientResponse(chatResponse, Map.of());
    }

    /** 创建一个没有 Usage 元数据的 mock 响应（防御性测试） */
    private ChatClientResponse createMockResponseWithoutUsage() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getMetadata()).thenReturn(null);
        return new ChatClientResponse(chatResponse, Map.of());
    }
}
