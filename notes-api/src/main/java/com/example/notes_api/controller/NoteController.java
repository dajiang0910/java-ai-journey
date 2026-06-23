package com.example.notes_api.controller;

import com.example.notes_api.domain.Note;
import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.CreateNoteRequest;
import com.example.notes_api.dto.NoteResponse;
import com.example.notes_api.dto.UpdateNoteRequest;
import com.example.notes_api.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteResponse> list() {
        return noteService.list().stream()
                .map(NoteResponse::from)
                .toList();
    }

    @PostMapping
    public ApiResponse<NoteResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        Note note = noteService.create(request.title(), request.content());
        return ApiResponse.success(NoteResponse.from(note));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteResponse> getById(@PathVariable Long id) {
        Note note = noteService.getById(id);
        return ApiResponse.success(NoteResponse.from(note));
    }

    @PutMapping("/{id}")
    public NoteResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNoteRequest request) {
        Note note = noteService.update(id, request.title(), request.content());
        return NoteResponse.from(note);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        noteService.delete(id);
    }
}
