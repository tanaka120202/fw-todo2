package com.example.todoapp2;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTests {
    @Mock TodoMapper mapper;
    @Mock TodoChangeMapper changes;
    @Mock TodoAuditService audit;
    @Mock TodoAttachmentService attachments;
    @InjectMocks TodoService service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesOnlyTheSignedInUsersTodos() {
        signIn(UserAccount.Role.USER);
        var from = LocalDate.of(2026, 4, 1);
        var to = from.plusDays(6);
        var todo = new Todo();
        when(mapper.search("design", "デザイン", "asc", from, to, 7L)).thenReturn(List.of(todo));

        assertThat(service.search("design", "デザイン", "asc", from, to)).containsExactly(todo);
        verify(mapper).search("design", "デザイン", "asc", from, to, 7L);
    }

    @Test
    void administratorCanReadWithoutAnOwnerFilter() {
        signIn(UserAccount.Role.ADMIN);
        var todo = new Todo();
        when(mapper.findById(20L, null)).thenReturn(todo);

        assertThat(service.findById(20L)).isSameAs(todo);
        verify(mapper).findById(20L, null);
    }

    @Test
    void refusesUnauthenticatedReadsBeforeCallingTheMapper() {
        assertThatThrownBy(() -> service.findById(20L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(mapper, audit, changes, attachments);
    }

    @Test
    void createsWithTheSignedInOwnerAndRecordsTheGeneratedIdInHistory() {
        signIn(UserAccount.Role.USER);
        var todo = new Todo();
        when(audit.recordAttempt("CREATE", null)).thenReturn("create-operation");
        when(mapper.insert(todo, 7L)).thenAnswer(call -> {
            todo.setId(20L);
            return 1;
        });

        assertThat(service.create(todo)).isEqualTo(1);
        var order = inOrder(audit, mapper, changes);
        order.verify(audit).recordAttempt("CREATE", null);
        order.verify(mapper).insert(todo, 7L);
        order.verify(changes).insertHistory("create-operation", 20L, "CREATE");
    }

    @Test
    void writesNoHistoryWhenUpdateMatchesNoAccessibleTodo() {
        signIn(UserAccount.Role.USER);
        var todo = new Todo();
        todo.setId(20L);
        when(mapper.update(todo, 7L)).thenReturn(0);

        assertThat(service.update(todo)).isZero();
        verify(audit).recordAttempt("UPDATE", 20L);
        verify(mapper).update(todo, 7L);
        verifyNoInteractions(changes);
    }

    @Test
    void deletesAndRecordsOnlyTheRowsLockedWithTheOwnerFilter() {
        signIn(UserAccount.Role.USER);
        var requested = List.of(20L, 21L);
        var allowed = List.of(20L);
        when(audit.recordAttempt("BULK_DELETE", null)).thenReturn("delete-operation");
        when(mapper.lockIds(requested, 7L)).thenReturn(allowed);
        when(mapper.deleteByIds(allowed, 7L)).thenReturn(1);

        assertThat(service.deleteByIds(requested)).isEqualTo(1);
        var order = inOrder(audit, mapper, attachments, changes);
        order.verify(audit).recordAttempt("BULK_DELETE", null);
        order.verify(mapper).lockIds(requested, 7L);
        order.verify(attachments).deleteFilesAfterCommit(allowed);
        order.verify(mapper).deleteByIds(allowed, 7L);
        order.verify(changes).insertHistory("delete-operation", 20L, "BULK_DELETE");
        verifyNoMoreInteractions(changes);
    }

    @Test
    void searchesWithoutDateFiltersWhenTheyAreOmitted() {
        signIn(UserAccount.Role.USER);
        var todo = new Todo();
        when(mapper.search("", "", "desc", null, null, 7L)).thenReturn(List.of(todo));

        assertThat(service.search("", "", "desc")).containsExactly(todo);
        verify(mapper).search("", "", "desc", null, null, 7L);
    }

    @Test
    void searchesWithAnExplicitAdministratorWithoutUsingTheSession() {
        var from = LocalDate.of(2026, 4, 1);
        var to = from.plusDays(1);
        when(mapper.search("missing", "", "asc", from, to, null)).thenReturn(List.of());

        assertThat(service.search("missing", "", "asc", from, to, principal(UserAccount.Role.ADMIN))).isEmpty();
        verify(mapper).search("missing", "", "asc", from, to, null);
    }

    @Test
    void returnsNullWhenTheExplicitUserCannotFindTheTodo() {
        when(mapper.findById(20L, 7L)).thenReturn(null);

        assertThat(service.findById(20L, principal(UserAccount.Role.USER))).isNull();
        verify(mapper).findById(20L, 7L);
    }

    @Test
    void refusesWritesWhenTheAuthenticatedPrincipalIsNotAnAccount() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("plain-user", null, List.of()));
        SecurityContextHolder.setContext(context);

        assertThatThrownBy(() -> service.create(new Todo())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(mapper, audit, changes, attachments);
    }

    @Test
    void doesNotWriteCreateHistoryWhenNoRowIsInserted() {
        signIn(UserAccount.Role.USER);
        var todo = new Todo();
        when(mapper.insert(todo, 7L)).thenReturn(0);

        assertThat(service.create(todo)).isZero();
        verify(audit).recordAttempt("CREATE", null);
        verify(mapper).insert(todo, 7L);
        verifyNoInteractions(changes, attachments);
    }

    @Test
    void propagatesAnInsertFailureWithoutWritingHistory() {
        signIn(UserAccount.Role.USER);
        var todo = new Todo();
        var failure = new IllegalStateException("test insert failure");
        when(mapper.insert(todo, 7L)).thenThrow(failure);

        assertThatThrownBy(() -> service.create(todo)).isSameAs(failure);
        verify(audit).recordAttempt("CREATE", null);
        verifyNoInteractions(changes, attachments);
    }

    @Test
    void recordsHistoryWhenAnAdministratorUpdatesATodo() {
        signIn(UserAccount.Role.ADMIN);
        var todo = new Todo();
        todo.setId(20L);
        when(audit.recordAttempt("UPDATE", 20L)).thenReturn("update-operation");
        when(mapper.update(todo, null)).thenReturn(1);

        assertThat(service.update(todo)).isEqualTo(1);
        var order = inOrder(audit, mapper, changes);
        order.verify(audit).recordAttempt("UPDATE", 20L);
        order.verify(mapper).update(todo, null);
        order.verify(changes).insertHistory("update-operation", 20L, "UPDATE");
    }

    @Test
    void deletesOneOwnedTodoAndSchedulesAttachmentCleanup() {
        signIn(UserAccount.Role.USER);
        var ids = List.of(20L);
        when(audit.recordAttempt("DELETE", 20L)).thenReturn("delete-operation");
        when(mapper.lockIds(ids, 7L)).thenReturn(ids);
        when(mapper.deleteById(20L, 7L)).thenReturn(1);

        assertThat(service.delete(20L)).isEqualTo(1);
        var order = inOrder(audit, mapper, attachments, changes);
        order.verify(audit).recordAttempt("DELETE", 20L);
        order.verify(mapper).lockIds(ids, 7L);
        order.verify(attachments).deleteFilesAfterCommit(ids);
        order.verify(mapper).deleteById(20L, 7L);
        order.verify(changes).insertHistory("delete-operation", 20L, "DELETE");
    }

    @Test
    void skipsDeletionIfTheTodoCannotBeLockedWithTheOwnerFilter() {
        signIn(UserAccount.Role.USER);
        var ids = List.of(20L);
        when(mapper.lockIds(ids, 7L)).thenReturn(List.of());

        assertThat(service.delete(20L)).isZero();
        verify(audit).recordAttempt("DELETE", 20L);
        verify(mapper).lockIds(ids, 7L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(changes, attachments);
    }

    @Test
    void recordsNoDeleteHistoryIfTheMapperReportsZeroRowsAfterLocking() {
        signIn(UserAccount.Role.USER);
        var ids = List.of(20L);
        when(mapper.lockIds(ids, 7L)).thenReturn(ids);
        when(mapper.deleteById(20L, 7L)).thenReturn(0);

        assertThat(service.delete(20L)).isZero();
        verify(mapper).deleteById(20L, 7L);
        verifyNoInteractions(changes);
    }

    @Test
    void bulkDeleteWithAnEmptySelectionDoesNotAccessStorage() {
        signIn(UserAccount.Role.USER);

        assertThat(service.deleteByIds(List.of())).isZero();
        verifyNoInteractions(mapper, audit, changes, attachments);
    }

    @Test
    void bulkDeleteKeepsOnlyTheAuditAttemptWhenNoRowsCanBeLocked() {
        signIn(UserAccount.Role.USER);
        var ids = List.of(20L, 21L);
        when(mapper.lockIds(ids, 7L)).thenReturn(List.of());

        assertThat(service.deleteByIds(ids)).isZero();
        verify(audit).recordAttempt("BULK_DELETE", null);
        verify(mapper).lockIds(ids, 7L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(changes, attachments);
    }

    @Test
    void bulkDeleteWritesHistoryForEachDeletedTodo() {
        signIn(UserAccount.Role.ADMIN);
        var ids = List.of(20L, 21L);
        when(audit.recordAttempt("BULK_DELETE", null)).thenReturn("bulk-operation");
        when(mapper.lockIds(ids, null)).thenReturn(ids);
        when(mapper.deleteByIds(ids, null)).thenReturn(2);

        assertThat(service.deleteByIds(ids)).isEqualTo(2);
        verify(attachments).deleteFilesAfterCommit(ids);
        verify(changes).insertHistory("bulk-operation", 20L, "BULK_DELETE");
        verify(changes).insertHistory("bulk-operation", 21L, "BULK_DELETE");
        verifyNoMoreInteractions(changes);
    }

    private AccountPrincipal principal(UserAccount.Role role) {
        var user = new UserAccount();
        user.setId(7L);
        user.setUsername("unit-test");
        user.setPasswordHash("unused-test-hash");
        user.setRole(role);
        user.setEnabled(true);
        return new AccountPrincipal(user);
    }

    private void signIn(UserAccount.Role role) {
        var principal = principal(role);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
