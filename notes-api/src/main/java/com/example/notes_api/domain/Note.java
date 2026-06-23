package com.example.notes_api.domain;

public class Note {
    private Long id;
    private String title;
    private String content;

    public Note(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }
    // getter 全套(Jackson 序列化要用);Day3 做 update 时再加 setter
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
}
