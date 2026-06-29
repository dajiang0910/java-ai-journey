package com.example.notes_api.service;

import org.springframework.ai.chat.client.ChatClient;
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
}
