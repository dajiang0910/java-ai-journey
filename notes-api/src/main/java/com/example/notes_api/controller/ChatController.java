package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.ChatRequest;
import com.example.notes_api.dto.SlugRequest;
import com.example.notes_api.dto.SummarizeRequest;
import com.example.notes_api.dto.TranslateRequest;
import com.example.notes_api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
