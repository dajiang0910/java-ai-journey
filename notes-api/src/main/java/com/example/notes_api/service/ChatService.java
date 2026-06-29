package com.example.notes_api.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * LLM 对话服务。
 * <p>
 * 核心理念：ChatClient 就像 JpaRepository —— 注入 → 调用 → 拿结果。
 * 底层是 OpenAI 官方 Java SDK（openai-java-core），Spring AI 把它封装成 Spring Bean。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    /**
     * 对话记忆 —— 存储所有会话的历史消息。
     * <p>
     * 由 {@code ChatMemoryAutoConfiguration} 自动配置（InMemory 实现），
     * 同一个 Bean 支撑所有 conversationId 的增删查操作。
     * <p>
     * 类比：就像 HTTP Session 存储，按 sessionId 隔离不同用户的数据。
     */
    private final ChatMemory chatMemory;

    /**
     * 构造器注入 ChatClient.Builder + ChatMemory。
     * <p>
     * ChatMemory 是 Day 4 新增依赖 —— Spring AI 自动配置会创建一个
     * InMemoryChatMemory Bean，注入后直接可用，无需额外配置。
     */
    public ChatService(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
    }

    /**
     * 同步对话 —— 发送消息，阻塞等待完整回复。
     * <p>
     * 适用场景：快速问答、AI 润色、翻译等不需要流式的场景。
     *
     * @param userMessage 用户输入的消息
     * @return LLM 的完整回复文本
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)   // 设置用户消息（即 "user" 角色）
                .call()              // 同步调用，阻塞等待回复完成
                .content();          // 提取回复的纯文本内容
    }

    // ================================================================
    // Day 2 新增方法
    // ================================================================

    /**
     * 带 System 角色的翻译。
     * <p>
     * .system() 在最前面设定 AI 的行为规范：
     * "你是专业翻译助手 → 只翻译，不加解释"。
     * <p>
     * 对比 chat() 方法：这里 .system() 在前，约束了 AI 的输出格式。
     *
     * @param text           待翻译的文本
     * @param targetLanguage 目标语言（如 "英文"、"日语"）
     * @return 翻译结果（纯译文，无额外解释）
     */
    public String translate(String text, String targetLanguage) {
        return chatClient.prompt()
                .system("你是一个专业的翻译助手。请将用户提供的文本准确地翻译成" + targetLanguage
                        + "。只返回翻译结果，不要添加任何解释、注释或额外信息。")
                .user(text)
                .call()
                .content();
    }

    /**
     * 使用 PromptTemplate 生成摘要。
     * <p>
     * PromptTemplate 让提示词"参数化"：
     * <ol>
     *   <li>创建模板 —— 用 {maxWords} 和 {content} 做占位符</li>
     *   <li>填充占位符 —— .add("maxWords", 值) 填坑</li>
     *   <li>渲染 —— .render() 把占位符替换成实际值</li>
     * </ol>
     * 对比用 + 拼字符串：PromptTemplate 可读性强、不易出错、模板可复用。
     *
     * @param content  待摘要的长文本
     * @param maxWords 摘要最大字数
     * @return 摘要文本
     */
    public String summarize(String content, int maxWords) {
        // 1. 创建模板 —— {maxWords} 和 {content} 是占位符
        PromptTemplate template = new PromptTemplate("""
                请对以下内容进行摘要，要求：
                1. 不超过 {maxWords} 字
                2. 保留核心要点，去掉冗余描述
                3. 用中文输出

                内容：
                {content}
                """);

        // 2. 填充占位符（就像给 SQL 参数赋值）
        template.add("maxWords", maxWords);
        template.add("content", content);

        // 3. 渲染模板 → 得到最终提示词 → 发送给 LLM
        return chatClient.prompt()
                .user(template.render())
                .call()
                .content();
    }

    /**
     * System 角色 + PromptTemplate 结合使用。
     * <p>
     * System 设定角色（"你是命名专家"），PromptTemplate 参数化用户输入。
     * 两者互补：System 管"谁来做"，PromptTemplate 管"做什么"。
     *
     * @param title 文章标题
     * @return URL 友好的 slug（如 "spring-ai-quickstart"）
     */
    public String generateSlug(String title) {
        PromptTemplate template = new PromptTemplate("""
                请为以下标题生成一个 URL 友好的 slug：

                标题：{title}

                要求：
                - 全部小写英文
                - 单词之间用连字符 (-) 连接
                - 去除无意义的虚词（a, an, the, 的, 了 等）
                - 只返回 slug 本身，不要加任何其他内容
                """);

        template.add("title", title);

        return chatClient.prompt()
                .system("你是一个专业的 URL 命名专家，擅长将中文标题转换为简洁、语义准确的英文 slug。")
                .user(template.render())
                .call()
                .content();
    }

    // ================================================================
    // Day 3 新增方法
    // ================================================================

    /**
     * 流式对话 —— 发送消息，LLM 边生成边返回。
     * <p>
     * {@code .stream().content()} 返回 {@link Flux}{@code <String>}，
     * 每个元素是 LLM 生成的一个文本片段（token 级别或更粗粒度）。
     * <p>
     * 对比 {@link #chat(String)}：
     * <ul>
     *   <li>{@code .call().content()} → {@code String}（同步阻塞，等完整回复）</li>
     *   <li>{@code .stream().content()} → {@code Flux<String>}（异步非阻塞，边生成边推送）</li>
     * </ul>
     * <p>
     * 适用场景：聊天对话、长篇生成、需要"打字机效果"提升体验的场景。
     *
     * @param userMessage 用户输入的消息
     * @return LLM 回复的文本流，前端逐 token 接收展示
     */
    public Flux<String> streamChat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .stream()       // Day 3 核心：.stream() 替代 .call()
                .content();     // 返回 Flux<String>，不是 String
    }

    // ================================================================
    // Day 4 新增方法
    // ================================================================

    /**
     * 多轮对话 —— 携带对话历史，让 AI "记住"之前的对话上下文。
     * <p>
     * <b>Day 4 核心认知</b>：LLM 每次推理是"无状态"的 —— 要把历史消息
     * 一起发给模型，它才能"知道"之前说过什么。
     * <p>
     * <b>实现方式</b>：{@link MessageChatMemoryAdvisor}（Advisor 模式）
     * <ol>
     *   <li>请求前：Advisor 从 {@link ChatMemory} 取出该 conversationId 的历史消息
     *       → 注入到 Prompt 中（system 之后、user 之前）</li>
     *   <li>请求后：Advisor 把本轮 user + assistant 消息存入 ChatMemory</li>
     * </ol>
     * <p>
     * <b>Advisor 模式类比</b>：Spring MVC 的 Interceptor / Servlet 的 Filter ——
     * 在核心调用（LLM 推理）前后织入横切逻辑（历史管理）。
     * <p>
     * <b>conversationId 的作用</b>：类似 HTTP Session ID，区分不同对话会话。
     * 同一个 conversationId 共享历史，不同 conversationId 互相隔离。
     *
     * @param conversationId 对话会话标识（同一 ID 共享历史，不同 ID 互相隔离）
     * @param userMessage    当前轮的用户输入
     * @return LLM 的完整回复（已考虑历史上下文）
     */
    public String chatMultiTurn(String conversationId, String userMessage) {
        return chatClient.prompt()
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()))
                .user(userMessage)
                .call()
                .content();
    }
}
