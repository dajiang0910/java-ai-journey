package com.example.notes_api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.notes_api.domain.Note;

/**
 * MyBatis-Plus Mapper：继承 BaseMapper 即拥有 CRUD + 分页
 * - selectById, insert, updateById, deleteById, selectList, selectPage ...
 * - 复杂查询可用 @Select 注解或 XML
 */
public interface NoteMapper extends BaseMapper<Note> {
    // 0 行代码，BaseMapper 已经提供了全部基础 CRUD
}
