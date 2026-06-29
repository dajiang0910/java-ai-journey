package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.ChatMultiTurnRequest;
import com.example.notes_api.dto.ChatRequest;
import com.example.notes_api.dto.SlugRequest;
import com.example.notes_api.dto.SummarizeRequest;
import com.example.notes_api.dto.TranslateRequest;
import com.example.notes_api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * LLM 对话接口。
 * <p>
 * 这是第一个"接 LLM"的端点 —— 和 CRUD 的 NoteController 结构一模一样：
 * Controller 只做路由 + 参数校验，真正调 LLM 的逻辑在 ChatService。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Chat", description = "LLM 对话接口")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 同步对话 —— POST /api/chat。
     * <p>
     * 请求体：{"message": "什么是依赖注入？"}
     * 响应体：{"code": 200, "message": "success", "data": "依赖注入是..."}
     * <p>
     * 同步意味着请求会阻塞 ~2-5 秒等 LLM 完整回复。
     * Day 3 会做流式版本（/api/chat/stream），边生成边返回。
     */
    @PostMapping("/chat")
    @Operation(summary = "同步对话", description = "发送消息，阻塞等待 LLM 完整回复")
    public ApiResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        String reply = chatService.chat(request.message());
        return ApiResponse.success(reply);
    }

    // ================================================================
    // Day 2 新增端点：翻译、摘要、Slug 生成
    // ================================================================

    /**
     * 翻译 —— POST /api/chat/translate。
     * <p>
     * 请求体：{"text": "依赖注入是一种设计模式", "targetLanguage": "英文"}
     * 响应体：{"code": 200, "data": "Dependency injection is a design pattern"}
     * <p>
     * 关键点：ChatService 内部通过 .system() 设定翻译助手的角色，
     * AI 被约束为"只翻译，不解释"，输出格式干净可控。
     */
    @PostMapping("/chat/translate")
    @Operation(summary = "翻译", description = "使用 system 角色约束 AI 做专业翻译")
    public ApiResponse<String> translate(@Valid @RequestBody TranslateRequest request) {
        String result = chatService.translate(request.text(), request.targetLanguage());
        return ApiResponse.success(result);
    }

    /**
     * 摘要 —— POST /api/chat/summarize。
     * <p>
     * 请求体：{"content": "长文本...", "maxWords": 100}
     * 响应体：{"code": 200, "data": "这是摘要内容..."}
     * <p>
     * 关键点：ChatService 内部使用 PromptTemplate，
     * 把 maxWords 和 content 作为参数填入模板，再发给 LLM。
     */
    @PostMapping("/chat/summarize")
    @Operation(summary = "摘要", description = "使用 PromptTemplate 模板化提示词生成摘要")
    public ApiResponse<String> summarize(@Valid @RequestBody SummarizeRequest request) {
        String result = chatService.summarize(request.content(), request.maxWords());
        return ApiResponse.success(result);
    }

    /**
     * Slug 生成 —— POST /api/chat/slug。
     * <p>
     * 请求体：{"title": "Spring AI 起步"}
     * 响应体：{"code": 200, "data": "spring-ai-quickstart"}
     * <p>
     * 关键点：System 角色 + PromptTemplate 结合 ——
     * System 设定"你是命名专家"，PromptTemplate 把 title 填入模板。
     */
    @PostMapping("/chat/slug")
    @Operation(summary = "Slug 生成", description = "System 角色 + PromptTemplate 结合生成 URL 友好标识符")
    public ApiResponse<String> generateSlug(@Valid @RequestBody SlugRequest request) {
        String result = chatService.generateSlug(request.title());
        return ApiResponse.success(result);
    }

    // ================================================================
    // Day 3 新增端点：SSE 流式对话
    // ================================================================

    /**
     * 流式对话 —— GET /api/chat/stream?message=xxx。
     * <p>
     * 这是 Day 3 的核心端点：把 {@code .call()} 替换成 {@code .stream()}，
     * 返回 {@link Flux}{@code <String>} 并声明 {@link MediaType#TEXT_EVENT_STREAM_VALUE}，
     * Spring MVC 自动将每个字符串片段包装成 SSE {@code data: ...} 事件。
     * <p>
     * 用 curl 测试会更明显看到逐 token 输出效果（浏览器会缓冲）：
     * <pre>{@code
     * curl -N "http://localhost:8080/api/chat/stream?message=什么是依赖注入"
     * }</pre>
     * 前端使用 {@code EventSource} API：
     * <pre>{@code
     * const es = new EventSource("/api/chat/stream?message=你好");
     * es.onmessage = e => console.log(e.data); // 每收到一个 token 打印一次
     * }</pre>
     *
     * @param message 用户输入的消息（通过 URL 查询参数传递）
     * @return LLM 回复的文本流（SSE 格式）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话", description = "SSE 流式返回 LLM 回复，边生成边输出")
    public Flux<String> streamChat(@RequestParam String message) {
        return chatService.streamChat(message);
    }

    // ================================================================
    // Day 4 新增端点：多轮对话（消息历史）
    // ================================================================

    /**
     * 多轮对话 —— POST /api/chat/multi-turn。
     * <p>
     * 这是 Day 4 的核心端点：通过 {@code conversationId} 关联对话历史，
     * {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}
     * 自动在请求前注入历史消息、响应后保存新消息。
     * <p>
     * <b>使用方式</b>：
     * <pre>{@code
     * // 第 1 轮 —— 告诉 AI 你的名字
     * POST /api/chat/multi-turn
     * {"conversationId": "conv-001", "message": "我叫张三"}
     *
     * // 第 2 轮 —— AI 能记住你的名字
     * POST /api/chat/multi-turn
     * {"conversationId": "conv-001", "message": "我叫什么名字？"}
     * // → "你叫张三"（AI 记住了！）
     *
     * // 第 3 轮 —— 换个 conversationId 就"失忆"了
     * POST /api/chat/multi-turn
     * {"conversationId": "conv-002", "message": "我叫什么名字？"}
     * // → "我不知道你的名字"（新会话，无历史）
     * }</pre>
     * <p>
     * <b>与 Day 1-3 端点的关系</b>：
     * <ul>
     *   <li>{@code POST /api/chat} —— 同步单轮（无记忆）</li>
     *   <li>{@code GET /api/chat/stream} —— 流式单轮（无记忆）</li>
     *   <li>{@code POST /api/chat/multi-turn} —— 同步多轮（有记忆 ✅）</li>
     * </ul>
     *
     * @param request 包含 conversationId 和 message 的请求体
     * @return LLM 的完整回复（已考虑历史上下文）
     */
    @PostMapping("/chat/multi-turn")
    @Operation(summary = "多轮对话", description = "携带对话历史的多轮对话，AI 能记住之前的上下文")
    public ApiResponse<String> chatMultiTurn(@Valid @RequestBody ChatMultiTurnRequest request) {
        String reply = chatService.chatMultiTurn(request.conversationId(), request.message());
        return ApiResponse.success(reply);
    }
}
