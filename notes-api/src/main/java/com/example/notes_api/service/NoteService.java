package com.example.notes_api.service;

import com.example.notes_api.domain.Note;
import com.example.notes_api.repository.NoteRepository;
import org.springframework.stereotype.Service;

@Service
public class NoteService {

    private final NoteRepository noteRepository;   // 依赖的是「接口」,不是实现

    public NoteService(NoteRepository noteRepository) {   // 构造器注入
        this.noteRepository = noteRepository;
    }

    public Note getById(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() ->                       // Day4 换成自定义异常+全局处理
                        new RuntimeException("Note not found: " + id));
    }
}
