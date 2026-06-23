package com.example.notes_api.dto;

import com.example.notes_api.domain.Note;
import java.time.LocalDateTime;

public record NoteResponse(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt
) {
    // 静态工厂方法：Entity → DTO
    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getCreatedAt()
        );
    }
}