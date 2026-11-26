package com.example.todoapp2;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoChangeMapper {
    @Insert("""
            INSERT INTO todo_history (operation_id, todo_id, operation)
            VALUES (#{operationId}, #{todoId}, #{operation})
            """)
    void insertHistory(@Param("operationId") String operationId, @Param("todoId") Long todoId,
                       @Param("operation") String operation);

    @Insert("""
            INSERT INTO todo_audit_log (operation_id, operation, requested_todo_id)
            VALUES (#{operationId}, #{operation}, #{todoId,jdbcType=BIGINT})
            """)
    void insertAudit(@Param("operationId") String operationId, @Param("operation") String operation,
                     @Param("todoId") Long todoId);
}
