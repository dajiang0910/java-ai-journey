package com.example.notes_api.repository;

import com.example.notes_api.domain.Note;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryNoteRepository implements NoteRepository {

    private final Map<Long, Note> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);

    @PostConstruct
    void seed() {                          // Bean 创建后自动跑一次,塞两条种子数据
        save(new Note(null, "第一条笔记", "Hello Spring 三层架构"));
        save(new Note(null, "第二条笔记", "构造器注入真香"));
    }

    @Override
    public Note save(Note note) {
        Long id = (note.getId() == null) ? idGen.incrementAndGet() : note.getId();
        Note saved = new Note(id, note.getTitle(), note.getContent());
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Note> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Note> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
