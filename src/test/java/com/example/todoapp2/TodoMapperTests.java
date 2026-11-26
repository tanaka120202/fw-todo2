package com.example.todoapp2;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "spring.test.database.replace=NONE",
        "spring.datasource.url=jdbc:h2:mem:todo-mapper-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:user-management-test.sql"
})
@Transactional
class TodoMapperTests {
    @Autowired TodoMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private long ownerId;
    private long otherOwnerId;

    @BeforeEach
    void setUpOwners() {
        ownerId = addOwner("mapper-owner");
        otherOwnerId = addOwner("mapper-other");
    }

    @Test
    void insertsWithGeneratedIdAndRestrictsUpdatesAndDeletesByOwner() {
        Todo todo = insert("Mapper task", LocalDate.of(2026, 5, 3), ownerId);

        assertThat(todo.getId()).isPositive();
        assertThat(mapper.findById(todo.getId(), ownerId).getCompleted()).isFalse();
        assertThat(mapper.findById(todo.getId(), otherOwnerId)).isNull();
        todo.setTitle("Updated task");
        todo.setCompleted(true);
        assertThat(mapper.update(todo, otherOwnerId)).isZero();
        assertThat(mapper.update(todo, ownerId)).isEqualTo(1);
        Todo saved = mapper.findById(todo.getId(), ownerId);
        assertThat(saved.getTitle()).isEqualTo("Updated task");
        assertThat(saved.getCompleted()).isTrue();
        assertThat(mapper.deleteById(todo.getId(), otherOwnerId)).isZero();
        assertThat(mapper.deleteById(todo.getId(), ownerId)).isEqualTo(1);
        assertThat(mapper.findById(todo.getId(), null)).isNull();
    }

    @Test
    void filtersLiteralPercentAndSortsWithinDatesAndOwner() {
        var first = insert("Plan 10%", LocalDate.of(2026, 5, 3), ownerId);
        var last = insert("Plan 20%", LocalDate.of(2026, 5, 5), ownerId);
        insert("Plan 100", LocalDate.of(2026, 5, 4), ownerId);
        insert("Plan 30%", LocalDate.of(2026, 5, 6), ownerId);
        insert("Plan 40%", LocalDate.of(2026, 5, 4), otherOwnerId);

        assertThat(mapper.search("%", "デザイン", "desc",
                first.getDueDate(), last.getDueDate(), ownerId))
                .extracting(Todo::getId).containsExactly(last.getId(), first.getId());
    }

    @Test
    void locksAndDeletesOnlyAccessibleIdsInBulk() {
        var owned = insert("Owned", LocalDate.of(2026, 5, 3), ownerId);
        var other = insert("Other", LocalDate.of(2026, 5, 4), otherOwnerId);
        var requested = List.of(owned.getId(), other.getId());

        assertThat(mapper.lockIds(requested, ownerId)).containsExactly(owned.getId());
        assertThat(mapper.deleteByIds(requested, ownerId)).isEqualTo(1);
        assertThat(mapper.findById(owned.getId(), null)).isNull();
        assertThat(mapper.findById(other.getId(), null)).isNotNull();
    }

    @AfterTransaction
    void confirmsTestDataWasRolledBack() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    private long addOwner(String username) {
        jdbc.update("INSERT INTO users (username, password_hash, role) VALUES (?, 'unused-test-hash', 'USER')", username);
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private Todo insert(String title, LocalDate dueDate, long userId) {
        var todo = new Todo();
        todo.setTitle(title);
        todo.setCategory("デザイン");
        todo.setPriority(1);
        todo.setDueDate(dueDate);
        assertThat(mapper.insert(todo, userId)).isEqualTo(1);
        return todo;
    }
}
