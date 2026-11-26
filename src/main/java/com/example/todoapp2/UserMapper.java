package com.example.todoapp2;

import java.util.List;

import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, role, enabled FROM users ORDER BY username")
    List<UserAccount> findAll();

    @Select("SELECT id, username, role, enabled FROM users WHERE id = #{id}")
    UserAccount findById(long id);

    @Select("SELECT id, username, password_hash, role, enabled, version FROM users WHERE username = #{username}")
    UserAccount findByUsername(String username);

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id} AND enabled = TRUE AND version = #{version}")
    int isCurrent(@Param("id") long id, @Param("version") long version);

    @Insert("INSERT INTO users (username, password_hash, role) VALUES (#{username}, #{hash}, #{role})")
    void insert(@Param("username") String username, @Param("hash") String hash,
                @Param("role") UserAccount.Role role);

    @Update("""
            UPDATE users
            SET role = #{role}, enabled = #{enabled},
                password_hash = COALESCE(#{hash,jdbcType=VARCHAR}, password_hash),
                version = version + 1
            WHERE id = #{id}
            """)
    int update(@Param("id") long id, @Param("role") UserAccount.Role role,
               @Param("enabled") boolean enabled, @Param("hash") String hash);

    @Select("SELECT id FROM users WHERE id = #{id} FOR UPDATE")
    Long lockById(long id);

    @Select("SELECT id FROM todos WHERE user_id = #{id} ORDER BY id FOR UPDATE")
    List<Long> lockOwnedTodoIds(long id);

    @Delete("DELETE FROM users WHERE id = #{id} AND id <> #{actorId}")
    int delete(@Param("id") long id, @Param("actorId") long actorId);
}
