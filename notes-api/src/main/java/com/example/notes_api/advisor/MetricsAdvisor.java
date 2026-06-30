package com.example.notes_api.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

import java.util.concurrent.TimeUnit;

/**
 * 自定义 Advisor：采集 LLM 调用指标到 Micrometer。
 * <p>
 * 这是 Day 6 周末整合的核心产出 —— 从 SimpleLoggerAdvisor（调试工具）升级到
 * 生产级可观测（指标采集），回答面试高频题"你们怎么监控 LLM 调用？"
 *
 * <h3>采集指标</h3>
 * <ul>
 *   <li><b>llm.call.duration</b>（Timer）：每次 LLM 调用的耗时分布</li>
 *   <li><b>llm.call.tokens</b>（Counter）：输入 + 输出 Token 累计用量</li>
 *   <li><b>llm.call.count</b>（Counter）：LLM 调用总次数</li>
 * </ul>
 *
 * <h3>挂载方式</h3>
 * <pre>
 * chatClient.prompt()
 *     .advisors(a -> a.advisors(new MetricsAdvisor(meterRegistry)))
 *     .user(text)
 *     .call()
 *     .content();
 * </pre>
 * <p>
 * 和 SimpleLoggerAdvisor 一样，per-request 挂载，不影响全局 ChatClient。
 *
 * @see org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
 */
public class MetricsAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MetricsAdvisor.class);

    private final MeterRegistry meterRegistry;

    /** 模型名称（从 request 中拿不到时可设置默认值） */
    private final String defaultModel;

    public MetricsAdvisor(MeterRegistry meterRegistry) {
        this(meterRegistry, "unknown");
    }

    public MetricsAdvisor(MeterRegistry meterRegistry, String defaultModel) {
        this.meterRegistry = meterRegistry;
        this.defaultModel = defaultModel;
    }

    /**
     * 拦截 LLM 调用。
     * <p>
     * 执行顺序：
     * <pre>
     * before: 记录开始时间
     *   → chain.nextCall(request)  // 调用下一个 Advisor 或最终 LLM
     * after:  计算耗时 + 提取 Token → 上报 Micrometer
     * </pre>
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        // ── before：记录开始时间 ──
        long startNanos = System.nanoTime();

        // ── 调用下游 ──
        ChatClientResponse response = chain.nextCall(request);

        // ── after：计算指标并上报 ──
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // 上报 1：调用耗时（Timer，支持 P50/P95/P99 分位数）
        Timer.builder("llm.call.duration")
                .description("LLM 调用耗时")
                .tag("model", defaultModel)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // 上报 2：调用次数（Counter，累计值）
        Counter.builder("llm.call.count")
                .description("LLM 调用总次数")
                .tag("model", defaultModel)
                .register(meterRegistry)
                .increment();

        // 上报 3：Token 用量（从 ChatResponse metadata 中提取）
        try {
            if (response.chatResponse() != null
                    && response.chatResponse().getMetadata() != null) {
                Usage usage = response.chatResponse().getMetadata().getUsage();
                if (usage != null) {
                    // 输入 Token（Usage.getPromptTokens() 返回 Integer）
                    Integer promptTokens = usage.getPromptTokens();
                    if (promptTokens != null && promptTokens > 0) {
                        Counter.builder("llm.call.tokens")
                                .description("LLM 调用 Token 用量")
                                .tag("model", defaultModel)
                                .tag("type", "prompt")
                                .register(meterRegistry)
                                .increment(promptTokens.doubleValue());
                    }
                    // 输出 Token
                    Integer completionTokens = usage.getCompletionTokens();
                    if (completionTokens != null && completionTokens > 0) {
                        Counter.builder("llm.call.tokens")
                                .description("LLM 调用 Token 用量")
                                .tag("model", defaultModel)
                                .tag("type", "completion")
                                .register(meterRegistry)
                                .increment(completionTokens.doubleValue());
                    }
                    // 总 Token
                    Integer totalTokens = usage.getTotalTokens();
                    if (totalTokens != null && totalTokens > 0) {
                        Counter.builder("llm.call.tokens")
                                .description("LLM 调用 Token 用量")
                                .tag("model", defaultModel)
                                .tag("type", "total")
                                .register(meterRegistry)
                                .increment(totalTokens.doubleValue());
                    }
                }
            }
        } catch (Exception e) {
            // 指标采集失败不应影响业务调用
            log.warn("MetricsAdvisor 采集 Token 用量失败：{}", e.getMessage());
        }

        log.debug("MetricsAdvisor: model={}, duration={}ms, tokens={}",
                defaultModel, durationMs,
                getTotalTokens(response));

        return response;
    }

    /** 安全地提取总 Token 数（仅用于日志，Usage 返回 Integer 转为 Long 显示） */
    private Long getTotalTokens(ChatClientResponse response) {
        try {
            if (response.chatResponse() != null
                    && response.chatResponse().getMetadata() != null) {
                Usage usage = response.chatResponse().getMetadata().getUsage();
                if (usage != null) {
                    Integer total = usage.getTotalTokens();
                    return total != null ? total.longValue() : null;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── Advisor 接口其余方法 ──

    /**
     * Advisor 名称 —— 用于日志和调试。
     */
    @Override
    public String getName() {
        return "metricsAdvisor";
    }

    /**
     * 执行顺序。值越小越先执行（越外层）。
     * 使用 Ordered.HIGHEST_PRECEDENCE 让指标采集在最外层：
     * MetricsAdvisor（计时开始）→ SimpleLoggerAdvisor（日志）→ LLM
     * → SimpleLoggerAdvisor（日志）→ MetricsAdvisor（计时结束 + 上报）
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
