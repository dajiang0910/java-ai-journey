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
     * 从文本中提取结构化元数据（Day 1 版本，基础 Schema 约束）。
     * <p>
     * {@code .entity(NoteMetadata.class)} 是 Week 4 的核心 API ——
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
                .entity(NoteMetadata.class);  // ← Day 1 核心！一行替代手动解析
    }

    /**
     * 从文本中提取结构化元数据（Day 2 增强版：Few-shot + 后校验）。
     * <p>
     * 相比 Day 1 的 {@link #extract(String)}，Day 2 做了三件事：
     * <ol>
     *   <li><b>Few-shot 示例</b>：在 System Prompt 中嵌入 2 个真实范例，
     *       LLM 的模式匹配能力远强于指令跟随，示例的效果远超文字描述。</li>
     *   <li><b>@JsonPropertyDescription</b>：每个字段带上语义描述，
     *       BeanOutputConverter 会把这些描述写入 JSON Schema 的 description 字段，
     *       LLM 看到 Schema 时能精确理解每个字段的含义。</li>
     *   <li><b>后校验</b>：拿到结果后验证 category 是否在枚举范围内，
     *       不在则用正则兜底提取，避免 LLM 跑偏返回不可消费的数据。</li>
     * </ol>
     * <p>
     * <b>Few-shot 为什么有效？</b>
     * LLM 本质上是模式补全器（next-token predictor），不是指令执行器。
     * 给它看 2 个"输入 → 期望输出"的完整范例，它比读 200 字描述更清楚你要什么。
     *
     * @param content 原始文本内容
     * @return 结构化元数据（标题/关键词/分类/难度/摘要），经过校验
     */
    public NoteMetadata extractV2(String content) {
        NoteMetadata result = chatClient.prompt()
                .system("""
                        你是一个专业的文档分析专家。请从用户提供的内容中提取结构化信息。

                        ═══════════════════════════════════════════════
                        重要规则：
                        ═══════════════════════════════════════════════
                        1. 只返回 JSON，不要加任何解释、markdown 代码块标记
                        2. category 必须是以下五选一：技术、管理、产品、设计、其他
                        3. difficulty 必须是以下三选一：入门、中级、高级（可选）
                        4. keywords 必须 3-5 个，不要超过也不要少于
                        5. title 不超过 50 字，summary 不超过 100 字

                        ═══════════════════════════════════════════════
                        范例 1 —— 输入：
                        ═══════════════════════════════════════════════
                        Spring AI 是 Spring 生态的 AI 框架，它把大模型调用封装成普通的
                        Spring Bean，开发者可以用 ChatClient 进行对话、用 EmbeddingModel
                        做向量化、用 VectorStore 做相似检索。这套设计让 Java 开发者不需要
                        学习 Python 就能上手 LLM 应用开发。

                        → 期望输出：
                        {
                          "title": "Spring AI 框架介绍",
                          "keywords": ["Spring AI", "LLM", "ChatClient", "EmbeddingModel", "VectorStore"],
                          "category": "技术",
                          "difficulty": "入门",
                          "summary": "Spring AI 将大模型调用封装为 Spring Bean，让 Java 开发者零门槛上手 LLM 应用开发"
                        }

                        ═══════════════════════════════════════════════
                        范例 2 —— 输入：
                        ═══════════════════════════════════════════════
                        企业微信 2026 年 Q3 发布计划：7 月上线索管理，8 月上线数据分析仪表盘，
                        9 月全面接入 AI 助手。本次迭代重点解决客户管理混乱、数据不够实时的问题，
                        预计覆盖 2000+ 企业用户。

                        → 期望输出：
                        {
                          "title": "企业微信 Q3 发布计划",
                          "keywords": ["企业微信", "Q3计划", "线索管理", "数据分析", "AI助手"],
                          "category": "产品",
                          "difficulty": "中级",
                          "summary": "2026年Q3企业微信将上线线索管理、数据仪表盘和AI助手，覆盖超2000家企业用户"
                        }

                        ═══════════════════════════════════════════════
                        现在请按以上范例格式处理下面的输入：
                        ═══════════════════════════════════════════════
                        """)
                .user(content)
                .call()
                .entity(NoteMetadata.class);

        // === Day 2 新增：后校验 ===
        // LLM 仍可能把 category 写成不在枚举内的值（如 "编程"），
        // 这里做一层兜底校验，确保下游消费的数据是干净的。
        return validateAndFallback(result);
    }

    /**
     * 后校验 + 兜底修正。
     * <p>
     * 原则：Schema 约束大幅降低跑偏概率，但不能 100% 消除。
     * 生产级代码要再加一层"结果校验"，对关键字段做兜底。
     * <p>
     * 这是 Day 1 防跑偏四层策略的第五层：<b>结果后校验</b>。
     */
    private NoteMetadata validateAndFallback(NoteMetadata raw) {
        // 校验 category 在枚举范围内
        var validCategories = java.util.Set.of("技术", "管理", "产品", "设计", "其他");
        String fixedCategory = raw.category();
        if (raw.category() != null && !validCategories.contains(raw.category())) {
            // 兜底：如果不在枚举内，尝试模糊匹配 → 否则归为"其他"
            fixedCategory = switch (raw.category()) {
                case String c when c.contains("技术") || c.contains("编程") || c.contains("开发") -> "技术";
                case String c when c.contains("管理") || c.contains("运营") -> "管理";
                case String c when c.contains("产品") || c.contains("功能") -> "产品";
                case String c when c.contains("设计") || c.contains("UI") || c.contains("UX") -> "设计";
                default -> "其他";
            };
        }

        // 校验 keywords 数量（少于 3 个或超过 5 个时不做裁切，只留日志标记 ——
        // 生产环境可以接告警、人工审核）
        if (raw.keywords() != null && raw.keywords().size() < 3) {
            // 实际生产中可以触发重试或人工审核，这里只做标记
            System.err.println("[V2校验] 警告：关键词不足 3 个，实际 " + raw.keywords().size());
        }

        // 如果 category 被修正了，返回修正后的 record
        if (!fixedCategory.equals(raw.category())) {
            System.err.println("[V2校验] category 修正：" + raw.category() + " → " + fixedCategory);
            return new NoteMetadata(
                    raw.title(), raw.keywords(), fixedCategory,
                    raw.difficulty(), raw.summary());
        }

        return raw;
    }
}