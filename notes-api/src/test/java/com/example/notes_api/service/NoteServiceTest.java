package com.example.notes_api.service;

import com.example.notes_api.domain.Note;
import com.example.notes_api.exception.BusinessException;
import com.example.notes_api.repository.NoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NoteService 单元测试
 *
 * 核心思路：用 @Mock 创建假的 NoteRepository，
 * 让它按我们的"剧本"返回数据，只测 Service 自身逻辑。
 */
@ExtendWith(MockitoExtension.class)  // 启用 Mockito
class NoteServiceTest {

    @Mock
    NoteRepository noteRepository;   // 假的 Repository（替身演员）

    @InjectMocks
    NoteService noteService;         // 被测对象（真实注入 Mock）

    // ========== list() 测试 ==========

    @Test
    @DisplayName("list() 应返回所有笔记")
    void list_shouldReturnAllNotes() {
        // given：准备假数据
        Note note1 = new Note("标题1", "内容1");
        Note note2 = new Note("标题2", "内容2");
        when(noteRepository.findAll()).thenReturn(List.of(note1, note2));

        // when：调用被测方法
        List<Note> result = noteService.list();

        // then：验证结果
        assertEquals(2, result.size());
        assertEquals("标题1", result.get(0).getTitle());
        verify(noteRepository, times(1)).findAll();  // 确认调了 1 次
    }

    // ========== getById() 测试 ==========

    @Test
    @DisplayName("getById() 存在时应返回笔记")
    void getById_whenExists_shouldReturnNote() {
        // given
        Note note = new Note("测试标题", "测试内容");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // when
        Note result = noteService.getById(1L);

        // then
        assertEquals("测试标题", result.getTitle());
    }

    @Test
    @DisplayName("getById() 不存在时应抛 BusinessException")
    void getById_whenNotExists_shouldThrowBusinessException() {
        // given：findById 返回空
        when(noteRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then：调用应该抛 BusinessException，且状态码 404
        BusinessException ex = assertThrows(BusinessException.class,
                () -> noteService.getById(999L));
        assertTrue(ex.getMessage().contains("999"));
        assertEquals(404, ex.getStatus().value());
    }

    // ========== create() 测试 ==========

    @Test
    @DisplayName("create() 应保存并返回新笔记")
    void create_shouldSaveAndReturnNote() {
        // given：save 返回保存后的对象
        Note savedNote = new Note("新标题", "新内容");
        when(noteRepository.save(any(Note.class))).thenReturn(savedNote);

        // when
        Note result = noteService.create("新标题", "新内容");

        // then
        assertEquals("新标题", result.getTitle());
        assertEquals("新内容", result.getContent());
        verify(noteRepository, times(1)).save(any(Note.class));
    }

    // ========== update() 测试 ==========

    @Test
    @DisplayName("update() 存在时应更新并返回笔记")
    void update_whenExists_shouldUpdateAndReturn() {
        // given
        Note existing = new Note("旧标题", "旧内容");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Note result = noteService.update(1L, "新标题", "新内容");

        // then
        assertEquals("新标题", result.getTitle());
        assertEquals("新内容", result.getContent());
        verify(noteRepository).save(existing);
    }

    // ========== delete() 测试 ==========

    @Test
    @DisplayName("delete() 应调用 repository 删除")
    void delete_shouldCallRepository() {
        // when
        noteService.delete(1L);

        // then：验证 deleteById 被调用，参数是 1L
        verify(noteRepository).deleteById(1L);
    }
}
