package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.ChatRequest;
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
}
