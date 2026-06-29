package com.example.notes_api.controller;

import com.example.notes_api.dto.ChatCostResponse;
import com.example.notes_api.dto.NoteMetadata;
import com.example.notes_api.dto.SmartNoteResponse;
import com.example.notes_api.service.ChatService;
import com.example.notes_api.service.StructuredExtractService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 控制器测试 —— Day 6 综合实战补齐。
 * <p>
 * 覆盖 Day 1 → Day 6 全部 8 个端点（7 个同步 + 1 个流式）。
 * 使用 @WebMvcTest 只加载 Controller 层，@MockitoBean 模拟 ChatService。
 * <p>
 * 测试分层原则：
 * <ul>
 *   <li>Controller 测试：验证 HTTP 契约（URL/方法/状态码/响应结构），mock Service</li>
 *   <li>Service 测试：验证业务逻辑（Prompt 模板/Token 聚合），mock ChatClient</li>
 * </ul>
 * Day 6 先补齐 Controller 层测试，Service 层留给后续扩展。
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    StructuredExtractService extractService;

    // ================================================================
    // Day 1: POST /api/chat —— 基础同步对话
    // ================================================================

    @Test
    @DisplayName("POST /api/chat 应返回 ApiResponse<String> 格式的 LLM 回复")
    void chat_shouldReturnApiResponseWithReply() throws Exception {
        when(chatService.chat("什么是依赖注入？"))
                .thenReturn("依赖注入是一种设计模式...");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "什么是依赖注入？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("依赖注入是一种设计模式..."));
    }

    @Test
    @DisplayName("POST /api/chat 消息为空应返回 400（@NotBlank 校验）")
    void chat_withBlankMessage_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Day 2: POST /api/chat/translate —— System 角色翻译
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/translate 应返回翻译结果")
    void translate_shouldReturnTranslation() throws Exception {
        when(chatService.translate("依赖注入", "英文"))
                .thenReturn("Dependency Injection");

        mockMvc.perform(post("/api/chat/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "依赖注入", "targetLanguage": "英文"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Dependency Injection"));
    }

    // ================================================================
    // Day 2: POST /api/chat/summarize —— PromptTemplate 摘要
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/summarize 应返回摘要结果")
    void summarize_shouldReturnSummary() throws Exception {
        when(chatService.summarize("长文本内容...", 50))
                .thenReturn("这是摘要内容");

        mockMvc.perform(post("/api/chat/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "长文本内容...", "maxWords": 50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("这是摘要内容"));
    }

    // ================================================================
    // Day 2: POST /api/chat/slug —— System 角色 + PromptTemplate
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/slug 应返回 URL 友好的 slug")
    void slug_shouldReturnSlug() throws Exception {
        when(chatService.generateSlug("Spring AI 起步"))
                .thenReturn("spring-ai-quickstart");

        mockMvc.perform(post("/api/chat/slug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Spring AI 起步"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("spring-ai-quickstart"));
    }

    // ================================================================
    // Day 3: GET /api/chat/stream —— SSE 流式对话
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/stream 应返回 SSE 格式的文本流")
    void streamChat_shouldReturnSSEFlux() throws Exception {
        when(chatService.streamChat("你好"))
                .thenReturn(Flux.just("你", "好", "！"));

        // 注意：MockMvc 对 Flux 的测试返回完整结果（非逐 token）
        // 实际 SSE 效果需用 curl 验证
        mockMvc.perform(get("/api/chat/stream")
                        .param("message", "你好")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }

    // ================================================================
    // Day 4: POST /api/chat/multi-turn —— 多轮对话
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/multi-turn 应返回考虑历史的回复")
    void multiTurn_shouldReturnContextualReply() throws Exception {
        when(chatService.chatMultiTurn("conv-001", "我叫什么名字？"))
                .thenReturn("你叫张三");

        mockMvc.perform(post("/api/chat/multi-turn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId": "conv-001", "message": "我叫什么名字？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("你叫张三"));
    }

    @Test
    @DisplayName("POST /api/chat/multi-turn 缺少 conversationId 应返回 400")
    void multiTurn_withBlankConversationId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chat/multi-turn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId": "", "message": "你好"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Day 5: POST /api/chat/with-cost —— 带 Token 成本的对话
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/with-cost 应返回文本 + Token 用量")
    void chatWithCost_shouldReturnReplyAndTokens() throws Exception {
        ChatCostResponse mockResponse = new ChatCostResponse(
                "Java 是一种面向对象的编程语言",
                new ChatCostResponse.TokenUsage(150, 80, 230)
        );
        when(chatService.chatWithCost("用一句话介绍 Java"))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/chat/with-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "用一句话介绍 Java"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reply").value("Java 是一种面向对象的编程语言"))
                .andExpect(jsonPath("$.data.tokens.inputTokens").value(150))
                .andExpect(jsonPath("$.data.tokens.outputTokens").value(80))
                .andExpect(jsonPath("$.data.tokens.totalTokens").value(230));
    }

    // ================================================================
    // Day 6: POST /api/chat/smart-note —— 智能笔记发布助手（综合实战）
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/smart-note 应返回标题 + 摘要 + 翻译 + 聚合 Token")
    void smartNote_shouldReturnAllMetadataAndAggregatedTokens() throws Exception {
        SmartNoteResponse mockResponse = new SmartNoteResponse(
                "Spring AI 入门指南",
                "Spring AI 将 LLM 封装为 Spring Bean，提供 ChatClient 等核心能力",
                "Spring AI Quick Start Guide",
                new ChatCostResponse.TokenUsage(450, 180, 630)
        );
        when(chatService.smartNote("Spring AI 是 Spring 生态的 AI 框架..."))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/chat/smart-note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Spring AI 是 Spring 生态的 AI 框架..."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Spring AI 入门指南"))
                .andExpect(jsonPath("$.data.summary").value("Spring AI 将 LLM 封装为 Spring Bean，提供 ChatClient 等核心能力"))
                .andExpect(jsonPath("$.data.translation").value("Spring AI Quick Start Guide"))
                .andExpect(jsonPath("$.data.tokens.inputTokens").value(450))
                .andExpect(jsonPath("$.data.tokens.outputTokens").value(180))
                .andExpect(jsonPath("$.data.tokens.totalTokens").value(630));
    }

    @Test
    @DisplayName("POST /api/chat/smart-note 内容为空应返回 400")
    void smartNote_withBlankContent_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chat/smart-note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Week 4 Day 1: POST /api/extract/metadata —— 结构化提取
    // ================================================================

    @Test
    @DisplayName("POST /api/extract/metadata 应返回结构化 NoteMetadata")
    void extractMetadata_shouldReturnStructuredBean() throws Exception {
        NoteMetadata mockMeta = new NoteMetadata(
                "Spring AI 入门指南",
                List.of("Spring AI", "LLM", "ChatClient"),
                "技术",
                "入门",
                "Spring AI 将 LLM 封装为 Spring Bean"
        );
        when(extractService.extract("Spring AI 是 Spring 生态的 AI 框架..."))
                .thenReturn(mockMeta);

        mockMvc.perform(post("/api/extract/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Spring AI 是 Spring 生态的 AI 框架..."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Spring AI 入门指南"))
                .andExpect(jsonPath("$.data.keywords[0]").value("Spring AI"))
                .andExpect(jsonPath("$.data.keywords[1]").value("LLM"))
                .andExpect(jsonPath("$.data.keywords[2]").value("ChatClient"))
                .andExpect(jsonPath("$.data.category").value("技术"))
                .andExpect(jsonPath("$.data.difficulty").value("入门"))
                .andExpect(jsonPath("$.data.summary").value("Spring AI 将 LLM 封装为 Spring Bean"));
    }

    @Test
    @DisplayName("POST /api/extract/metadata 内容为空应返回 400")
    void extractMetadata_withBlankContent_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/extract/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
