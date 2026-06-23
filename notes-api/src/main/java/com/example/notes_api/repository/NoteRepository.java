package com.example.notes_api.repository;

import com.example.notes_api.domain.Note;
import java.util.List;
import java.util.Optional;

public interface NoteRepository {
    Optional<Note> findById(Long id);   // 今天主用
    List<Note> findAll();
    Note save(Note note);               // 今天用来塞种子数据
    void deleteById(Long id);           // Day3 用
}
