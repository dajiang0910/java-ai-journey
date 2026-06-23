package com.example.notes_api.domain;
import java.time.LocalDateTime;

public class Note {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    public Note(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
    // getter 全套(Jackson 序列化要用);Day3 做 update 时再加 setter
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
}
