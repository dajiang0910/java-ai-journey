package com.example.notes_api.controller;

import com.example.notes_api.dto.*;
import com.example.notes_api.service.ChatService;
import com.example.notes_api.service.StructuredExtractService;
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

    private final StructuredExtractService extractService;

    // 修改构造器，新增 StructuredExtractService 参数
    public ChatController(ChatService chatService, StructuredExtractService extractService) {
        this.chatService = chatService;
        this.extractService = extractService;
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

    // ================================================================
    // Day 5 新增端点：带 Token 成本信息的对话
    // ================================================================

    /**
     * 带 Token 成本信息的同步对话 —— POST /api/chat/with-cost。
     * <p>
     * 这是 Day 5 的核心端点：相比 {@code POST /api/chat}（只返回纯文本），
     * 额外返回每次调用的 Token 用量（输入/输出/总量），让调用方能感知 LLM 成本。
     * <p>
     * <b>请求</b>：{@code {"message": "用一句话介绍 Java"}}
     * <p>
     * <b>响应</b>：
     * <pre>{@code
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "reply": "Java 是一种面向对象的编程语言...",
     *     "tokens": {
     *       "inputTokens": 150,
     *       "outputTokens": 80,
     *       "totalTokens": 230
     *     }
     *   }
     * }
     * }</pre>
     * <p>
     * <b>与 Day 1 端点的关系</b>：
     * <ul>
     *   <li>{@code POST /api/chat} —— 只返回文本（简单场景）</li>
     *   <li>{@code POST /api/chat/with-cost} —— 返回文本 + Token 用量（成本追踪）</li>
     * </ul>
     * <p>
     * <b>底层实现</b>：ChatService 使用 {@code .chatClientResponse()} 而非 {@code .content()}，
     * 从 {@link org.springframework.ai.chat.model.ChatResponse#getMetadata()} 中提取
     * {@link org.springframework.ai.chat.metadata.Usage} 得到 Token 统计。
     *
     * @param request 包含 message 的请求体（复用 Day 1 的 ChatRequest）
     * @return 包含回复文本和 Token 用量的响应
     */
    @PostMapping("/chat/with-cost")
    @Operation(summary = "带 Token 成本的同步对话",
            description = "返回 AI 回复 + Token 用量（输入/输出/总量），用于成本追踪与监控")
    public ApiResponse<ChatCostResponse> chatWithCost(@Valid @RequestBody ChatRequest request) {
        ChatCostResponse response = chatService.chatWithCost(request.message());
        return ApiResponse.success(response);
    }

    // ================================================================
    // Day 6 新增端点：智能笔记发布助手（综合实战）
    // ================================================================

    /**
     * 智能笔记发布助手 —— POST /api/chat/smart-note。
     * <p>
     * 这是 Day 6 综合实战的核心端点：输入原始笔记内容，AI 自动完成
     * <b>标题生成 → 摘要提取 → 英文翻译</b> 三步流水线，返回聚合结果 + Token 成本。
     * <p>
     * <b>与 Day 1-5 端点的关系</b>：
     * <ul>
     *   <li>{@code POST /api/chat} —— 基础对话（Day 1）</li>
     *   <li>{@code POST /api/chat/translate / summarize / slug} —— 单功能调用（Day 2）</li>
     *   <li>{@code GET /api/chat/stream} —— 流式对话（Day 3）</li>
     *   <li>{@code POST /api/chat/multi-turn} —— 多轮对话（Day 4）</li>
     *   <li>{@code POST /api/chat/with-cost} —— 带成本追踪的单次对话（Day 5）</li>
     *   <li><b>{@code POST /api/chat/smart-note} —— 多步 AI 流水线 + Token 聚合（Day 6）</b> 🆕</li>
     * </ul>
     * <p>
     * <b>请求示例</b>：
     * <pre>{@code
     * {
     *   "content": "Spring AI 是 Spring 生态的 AI 框架，它把 LLM 调用封装成普通 Bean，
     *              支持 ChatClient、Embedding、VectorStore、Tool Calling 等能力..."
     * }
     * }</pre>
     * <p>
     * <b>响应示例</b>：
     * <pre>{@code
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "title": "Spring AI：把大模型变成 Spring Bean",
     *     "summary": "Spring AI 将 LLM 封装为 Spring Bean，提供 ChatClient 等核心能力...",
     *     "translation": "Spring AI is the AI framework in the Spring ecosystem...",
     *     "tokens": {
     *       "inputTokens": 450,
     *       "outputTokens": 150,
     *       "totalTokens": 600
     *     }
     *   }
     * }
     * }</pre>
     * <p>
     * <b>Token 聚合逻辑</b>：{@code total = step1 + step2 + step3}，
     * 详见 {@link com.example.notes_api.service.ChatService#smartNote(String)}。
     *
     * @param request 包含原始笔记内容的请求体
     * @return 包含标题、摘要、翻译和聚合 Token 用量的响应
     */
    @PostMapping("/chat/smart-note")
    @Operation(summary = "智能笔记发布助手",
            description = "输入原始内容，AI 自动生成标题 + 摘要 + 英文翻译（多步流水线 + Token 聚合）")
    public ApiResponse<SmartNoteResponse> smartNote(@Valid @RequestBody SmartNoteRequest request) {
        SmartNoteResponse response = chatService.smartNote(request.content());
        return ApiResponse.success(response);
    }

    // ================================================================
    // Week 4 Day 1：结构化提取（基础版 —— Schema 约束）
    // ================================================================

    @PostMapping("/extract/metadata")
    @Operation(summary = "结构化提取元数据（v1·基础）",
            description = "输入文本，AI 自动提取标题/关键词/分类/难度/摘要。底层用 .entity(Class) 生成 JSON Schema 约束 LLM 输出")
    public ApiResponse<NoteMetadata> extractMetadata(@Valid @RequestBody ExtractRequest request) {
        return ApiResponse.success(extractService.extract(request.content()));
    }

    // ================================================================
    // Week 4 Day 2：结构化提取（增强版 —— Few-shot + 字段描述 + 后校验）
    // ================================================================

    @PostMapping("/extract/metadata/v2")
    @Operation(
            summary = "结构化提取元数据（v2·Few-shot 增强）",
            description = """
                    相比 v1 基础版，v2 做了三层增强：
                    1. Few-shot 范例 —— System Prompt 中嵌入 2 个「输入→期望输出」范例，LLM 照猫画虎
                    2. @JsonPropertyDescription —— 每个字段有语义描述，写入 JSON Schema
                    3. 后校验兜底 —— category 枚举校验 + 模糊匹配修正，确保下游消费者拿到干净数据
                    """
    )
    public ApiResponse<NoteMetadata> extractMetadataV2(@Valid @RequestBody ExtractRequest request) {
        return ApiResponse.success(extractService.extractV2(request.content()));
    }
}
