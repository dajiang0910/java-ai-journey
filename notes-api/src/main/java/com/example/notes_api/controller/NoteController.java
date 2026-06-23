package com.example.notes_api.controller;

import com.example.notes_api.domain.Note;
import com.example.notes_api.service.NoteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")     // 类级公共前缀
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {   // 又是构造器注入
        this.noteService = noteService;
    }

    @GetMapping("/{id}")                                 // 拼成 GET /api/notes/{id}
    public Note getById(@PathVariable Long id) {         // 路径变量绑定到参数
        return noteService.getById(id);
    }
}
