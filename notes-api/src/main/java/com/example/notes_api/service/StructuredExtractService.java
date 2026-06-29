package com.example.notes_api.service;

import com.example.notes_api.dto.NoteMetadata;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 结构化提取服务。
 * <p>
 * 核心理念：用 {@code .entity(Class)} 让 LLM 直接返回类型安全的 Java 对象，
 * 而不是返回 String 再手动解析 JSON。
 * <p>
 * 底层机制：Spring AI 的 {@code BeanOutputConverter}：
 * <ol>
 *   <li>扫描 Java 类的字段 + {@code @JsonProperty} 注解 → 生成 JSON Schema</li>
 *   <li>把 Schema 拼入 Prompt（告诉 LLM "请按这个格式回复"）</li>
 *   <li>拿到 LLM 回复后，自动用 Jackson 反序列化为 Java Bean</li>
 * </ol>
 */
@Service
public class StructuredExtractService {

    private final ChatClient chatClient;

    public StructuredExtractService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 从文本中提取结构化元数据。
     * <p>
     * {@code .entity(NoteMetadata.class)} 是 Day 4 的核心 API ——
     * 等价于 Week 3 的 {@code .content()} + 手动 Jackson 解析，
     * 但更安全：Schema 约束让 LLM 更不容易跑偏。
     *
     * @param content 原始文本内容
     * @return 结构化元数据（标题/关键词/分类/难度/摘要）
     */
    public NoteMetadata extract(String content) {
        return chatClient.prompt()
                .system("""
                          你是一个专业的文档分析专家。请从用户提供的内容中提取以下信息：

                          1. title — 内容的核心标题（不超过 50 字）
                          2. keywords — 3-5 个关键词（数组）
                          3. category — 内容分类，从以下选一：技术/管理/产品/设计/其他
                          4. difficulty — 内容难度：入门/中级/高级（可选）
                          5. summary — 一句话摘要（不超过 100 字）

                          重要：只返回 JSON，不要加任何解释或 markdown 代码块标记。
                          """)
                .user(content)
                .call()
                .entity(NoteMetadata.class);  // ← Day 4 核心！一行替代手动解析
    }
}