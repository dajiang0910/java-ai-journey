package com.example.notes_api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.notes_api.domain.Note;
import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.dto.CreateNoteRequest;
import com.example.notes_api.dto.NoteResponse;
import com.example.notes_api.dto.UpdateNoteRequest;
import com.example.notes_api.mapper.NoteMapper;
import com.example.notes_api.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "笔记管理", description = "笔记 CRUD 操作")
public class NoteController {
    private final NoteService noteService;
    private final NoteMapper noteMapper;  // MyBatis-Plus Mapper

    public NoteController(NoteService noteService, NoteMapper noteMapper) {
        this.noteService = noteService;
        this.noteMapper = noteMapper;
    }

    @GetMapping
    @Operation(summary = "获取所有笔记")
    public List<NoteResponse> list() {
        return noteService.list().stream()
                .map(NoteResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "创建笔记")
    public ApiResponse<NoteResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        Note note = noteService.create(request.title(), request.content());
        return ApiResponse.success(NoteResponse.from(note));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 获取笔记")
    public ApiResponse<NoteResponse> getById(@PathVariable Long id) {
        Note note = noteService.getById(id);
        return ApiResponse.success(NoteResponse.from(note));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新笔记")
    public NoteResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNoteRequest request) {
        Note note = noteService.update(id, request.title(), request.content());
        return NoteResponse.from(note);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记")
    public void delete(@PathVariable Long id) {
        noteService.delete(id);
    }

    // ========== 分页查询（JPA vs MyBatis-Plus 对比） ==========

    /** JPA 分页：GET /api/notes/jpa/page?page=1&size=10 */
    @GetMapping("/jpa/page")
    @Operation(summary = "JPA 分页查询")
    public org.springframework.data.domain.Page<Note> jpaPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return noteService.list(page - 1, size);  // JPA 页码从 0 开始，前端传 1 需 -1
    }

    /** MyBatis-Plus 分页：GET /api/notes/mp/page?page=1&size=10 */
    @GetMapping("/mp/page")
    @Operation(summary = "MyBatis-Plus 分页查询")
    public IPage<Note> mpPage(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size) {
        Page<Note> pageParam = new Page<>(page, size);  // MP 页码从 1 开始
        return noteMapper.selectPage(pageParam, null);
    }
}
