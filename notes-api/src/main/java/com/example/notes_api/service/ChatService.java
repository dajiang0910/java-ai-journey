package com.example.notes_api.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

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
     * 构造器注入 ChatClient.Builder。
     * <p>
     * Spring AI 自动配置会创建一个 ChatClient.Builder Bean，
     * 里面已经配置好了 API Key、模型、超时等（从 application.properties 读取）。
     */
    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
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
}
