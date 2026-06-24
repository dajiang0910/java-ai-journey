package com.example.notes_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notes")
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 10000)
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected Note() {}  // JPA 要求无参构造

    /** 创建新笔记（ID 由数据库自动生成） */
    public Note(String title, String content) {
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
