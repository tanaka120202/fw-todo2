package com.example.todoapp2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TodoReportAsyncTests.Config.class)
@ExtendWith(OutputCaptureExtension.class)
class TodoReportAsyncTests {
    @Autowired TodoService todos;
    @Autowired TodoReportService reports;
    @Autowired EmailWorker emailWorker;
    @Autowired @Qualifier("emailExecutor") ThreadPoolTaskExecutor emailExecutor;

    @Test
    void generatesCsvOnTheReportPoolAndEscapesCells() throws Exception {
        var actor = principal();
        String callerThread = Thread.currentThread().getName();
        when(todos.search("", "", "asc", null, null, actor)).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("todo-task-").isNotEqualTo(callerThread);
            var todo = new Todo();
            todo.setId(1L);
            todo.setTitle("  =2+2");
            todo.setCategory("Design, \"draft\"");
            return List.of(todo);
        });

        String csv = new String(reports.generate("", "", "asc", actor).get(5, TimeUnit.SECONDS),
                StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,title,category,priority,dueDate,completed\r\n")
                .contains("1,\"'  =2+2\",\"Design, \"\"draft\"\"\",,,false\r\n");
    }

    @Test
    void routesUncaughtVoidFailuresThroughTheHandlerWithoutLoggingArguments(CapturedOutput output) throws Exception {
        emailWorker.fail("private-test-argument");
        String worker = emailExecutor.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);

        assertThat(worker).startsWith("todo-email-");
        assertThat(output.getAll()).contains("Async failure method=EmailWorker.fail exception=IllegalStateException")
                .doesNotContain("private-test-argument", "private-test-message");
    }

    @Test
    void mapsTimeoutRejectionAndExecutionFailureToHttpStatuses() {
        var service = mock(TodoReportService.class);
        var actor = principal();
        var controller = new TodoReportController(service, Duration.ofMillis(5));
        when(service.generate("", "", "asc", actor))
                .thenReturn(new CompletableFuture<>())
                .thenThrow(new TaskRejectedException("test queue full"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test worker failure")));

        assertThatThrownBy(() -> controller.download("", "", "asc", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        assertThatThrownBy(() -> controller.download("", "", "asc", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> controller.download("", "", "asc", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private AccountPrincipal principal() {
        var user = new UserAccount();
        user.setId(7L);
        user.setUsername("report-test");
        user.setPasswordHash("unused-test-hash");
        user.setRole(UserAccount.Role.USER);
        user.setEnabled(true);
        return new AccountPrincipal(user);
    }

    @TestConfiguration
    @Import({AsyncConfig.class, TodoReportService.class})
    static class Config {
        @Bean
        TodoService todos() {
            return mock(TodoService.class);
        }

        @Bean
        EmailWorker emailWorker() {
            return new EmailWorker();
        }
    }

    static class EmailWorker {
        @Async("emailExecutor")
        public void fail(String argument) {
            throw new IllegalStateException("private-test-message");
        }
    }
}
