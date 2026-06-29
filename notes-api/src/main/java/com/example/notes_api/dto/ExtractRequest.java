package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtractRequest(
        @NotBlank(message = "提取内容不能为空")
        String content
) {}