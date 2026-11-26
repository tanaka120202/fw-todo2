package com.example.todoapp2;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoMapper {
    List<Todo> search(@Param("keyword") String keyword, @Param("category") String category,
                      @Param("order") String order, @Param("from") LocalDate from, @Param("to") LocalDate to,
                      @Param("ownerId") Long ownerId);

    int insert(@Param("todo") Todo todo, @Param("userId") long userId);

    Todo findById(@Param("id") Long id, @Param("ownerId") Long ownerId);

    int update(@Param("todo") Todo todo, @Param("ownerId") Long ownerId);

    int deleteById(@Param("id") Long id, @Param("ownerId") Long ownerId);

    List<Long> lockIds(@Param("ids") List<Long> ids, @Param("ownerId") Long ownerId);

    int deleteByIds(@Param("ids") List<Long> ids, @Param("ownerId") Long ownerId);
}
