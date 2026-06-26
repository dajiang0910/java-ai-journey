package com.example.notes_api.controller;

import com.example.notes_api.domain.Note;
import com.example.notes_api.mapper.NoteMapper;
import com.example.notes_api.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NoteController 单元测试
 *
 * @WebMvcTest 只加载 Controller 层，不启动数据库等。
 * @MockitoBean 创建假的 Service 和 Mapper（Spring Boot 4.x 新注解，替代 @MockBean）。
 */
@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired
    MockMvc mockMvc;              // 模拟 HTTP 请求的工具

    @MockitoBean
    NoteService noteService;      // 假的 Service

    @MockitoBean
    NoteMapper noteMapper;        // 假的 Mapper

    // ========== GET /api/notes ==========

    @Test
    @DisplayName("GET /api/notes 应返回笔记列表")
    void list_shouldReturnNoteList() throws Exception {
        // given
        Note note = new Note("测试标题", "测试内容");
        when(noteService.list()).thenReturn(List.of(note));

        // when & then
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("测试标题"));
    }

    // ========== POST /api/notes ==========

    @Test
    @DisplayName("POST /api/notes 应创建笔记并返回 200")
    void create_withValidData_shouldReturn200() throws Exception {
        // given
        Note saved = new Note("新标题", "新内容");
        when(noteService.create("新标题", "新内容")).thenReturn(saved);

        // when & then
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "新标题", "content": "新内容"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    @Test
    @DisplayName("POST /api/notes 标题为空应返回 400")
    void create_withBlankTitle_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "content": "内容"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ========== GET /api/notes/{id} ==========

    @Test
    @DisplayName("GET /api/notes/{id} 存在时应返回笔记")
    void getById_whenExists_shouldReturnNote() throws Exception {
        Note note = new Note("标题", "内容");
        when(noteService.getById(1L)).thenReturn(note);

        mockMvc.perform(get("/api/notes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("标题"));
    }

    // ========== PUT /api/notes/{id} ==========

    @Test
    @DisplayName("PUT /api/notes/{id} 应更新并返回笔记")
    void update_shouldReturnUpdatedNote() throws Exception {
        Note updated = new Note("更新后", "新内容");
        when(noteService.update(1L, "更新后", "新内容")).thenReturn(updated);

        mockMvc.perform(put("/api/notes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "更新后", "content": "新内容"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("更新后"));
    }

    // ========== DELETE /api/notes/{id} ==========

    @Test
    @DisplayName("DELETE /api/notes/{id} 应返回 200")
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/notes/1"))
                .andExpect(status().isOk());
    }
}
