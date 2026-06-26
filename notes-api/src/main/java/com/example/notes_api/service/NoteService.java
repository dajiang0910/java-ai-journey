package com.example.notes_api.service;

import com.example.notes_api.domain.Note;
import com.example.notes_api.exception.BusinessException;
import com.example.notes_api.repository.NoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NoteService {
    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public List<Note> list() {
        return noteRepository.findAll();
    }

    public Note getById(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "笔记不存在: " + id));
    }

    public Note create(String title, String content) {
        Note note = new Note(title, content);
        return noteRepository.save(note);
    }

    public Note update(Long id, String title, String content) {
        Note note = getById(id);
        note.setTitle(title);
        note.setContent(content);
        return noteRepository.save(note);
    }

    public void delete(Long id) {
        noteRepository.deleteById(id);
    }

    public Page<Note> list(int page, int size) {
        return noteRepository.findAll(PageRequest.of(page, size));
    }
}
