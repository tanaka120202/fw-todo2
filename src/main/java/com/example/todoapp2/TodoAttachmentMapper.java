package com.example.todoapp2;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TodoAttachmentMapper {
    @Select("""
            SELECT id, todo_id, original_name, size_bytes, created_at
            FROM todo_attachments WHERE todo_id = #{todoId} ORDER BY id
            """)
    List<TodoAttachment> list(long todoId);

    @Select("""
            SELECT id, todo_id, original_name, storage_name, size_bytes, created_at
            FROM todo_attachments WHERE todo_id = #{todoId} AND id = #{id}
            """)
    TodoAttachment find(@Param("todoId") long todoId, @Param("id") long id);

    @Insert("""
            INSERT INTO todo_attachments (todo_id, original_name, storage_name, size_bytes)
            VALUES (#{todoId}, #{originalName}, #{storageName}, #{sizeBytes})
            """)
    void insert(TodoAttachment attachment);

    @Delete("DELETE FROM todo_attachments WHERE todo_id = #{todoId} AND id = #{id}")
    void delete(@Param("todoId") long todoId, @Param("id") long id);

    @Select("""
            <script>
            SELECT storage_name FROM todo_attachments WHERE todo_id IN
            <foreach collection="todoIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            FOR UPDATE
            </script>
            """)
    List<String> storageNames(@Param("todoIds") List<Long> todoIds);
}
