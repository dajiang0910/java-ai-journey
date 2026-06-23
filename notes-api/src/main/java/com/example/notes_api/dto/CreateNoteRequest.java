package com.example.notes_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
        @NotBlank(message = "标题不能为空")
        @Size(min = 1, max = 100, message = "标题长度 1-100 字符")
        String title,

        @Size(max = 10000, message = "内容最多 10000 字符")
        String content
) {}
