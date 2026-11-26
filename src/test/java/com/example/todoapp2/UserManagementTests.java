package com.example.todoapp2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-management;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:user-management-test.sql",
        "TODO_AUTH_INITIAL_USERNAME=administrator",
        "TODO_AUTH_INITIAL_PASSWORD=admin-test-password",
        "TODO_AUTH_INITIAL_ROLE=ADMIN"
})
@ExtendWith(OutputCaptureExtension.class)
class UserManagementTests {
    @TempDir
    static Path attachmentDirectory;

    @DynamicPropertySource
    static void configureAttachments(DynamicPropertyRegistry properties) {
        properties.add("todo.attachments.directory", () -> attachmentDirectory.toString());
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserMapper users;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BCryptPasswordEncoder encoder;

    @Test
    void administratorManagesUsersAndOwnedTodos(CapturedOutput output) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        var admin = login(mvc, "administrator", "admin-test-password");
        MvcResult list = mvc.perform(get("/admin/users").session(admin)).andExpect(status().isOk()).andReturn();
        String token = csrf(list);
        mvc.perform(post("/admin/users").session(admin)
                .param("username", "member").param("password", "first-password"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users").session(admin).param("_csrf", token)
                .param("username", "member").param("password", "first-password"))
                .andExpect(redirectedUrl("/admin/users"));
        var member = users.findByUsername("member");
        long memberId = member.getId();
        assertThat(member.getRole()).isEqualTo(UserAccount.Role.USER);
        assertThat(encoder.matches("first-password", member.getPasswordHash())).isTrue();
        mvc.perform(post("/admin/users").session(admin).param("_csrf", token)
                .param("username", "member").param("password", "another-password"))
                .andExpect(flash().attributeExists("error"));
        assertThat(users.findByUsername("member").getPasswordHash()).isEqualTo(member.getPasswordHash());

        var memberSession = login(mvc, "member", "first-password");
        mvc.perform(get("/admin/users").session(memberSession)).andExpect(status().isForbidden());
        var todoPage = mvc.perform(get("/todos").session(memberSession)).andExpect(status().isOk()).andReturn();
        assertThat(todoPage.getResponse().getContentAsString()).doesNotContain("/admin/users");
        mvc.perform(post("/todos").session(memberSession).param("_csrf", csrf(todoPage))
                .param("title", "Member task").param("priority", "1")).andExpect(redirectedUrl("/todos"));
        mvc.perform(get("/todos").session(admin)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member task")));
        mvc.perform(post("/todos").session(admin).param("_csrf", token)
                .param("title", "Admin task").param("priority", "1")).andExpect(redirectedUrl("/todos"));
        String credentials = basicAuth("member", "first-password");
        mvc.perform(post("/api/todos").header("Authorization", credentials)
                .contentType("application/json").content("""
                        {"title":"Member API task","category":"デザイン","priority":1}
                        """))
                .andExpect(status().isCreated());
        assertMcpTodoScope(mvc, credentials);
        var csv = mvc.perform(get("/todos/report.csv").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andReturn();
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Member task").doesNotContain("Admin task");
        mvc.perform(get("/todos/report.csv").param("keyword", "API task").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member API task")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Member task"))));
        long adminTodoId = jdbc.queryForObject("SELECT id FROM todos WHERE title = 'Admin task'", Long.class);
        mvc.perform(get("/api/todos/{id}", adminTodoId).session(memberSession)).andExpect(status().isNotFound());
        mvc.perform(post("/todos/{id}/delete", adminTodoId).session(memberSession).param("_csrf", csrf(todoPage)))
                .andExpect(status().isNotFound()).andExpect(view().name("error"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE id = ?", Integer.class, adminTodoId))
                .isEqualTo(1);
        mvc.perform(get("/admin/users/{id}/edit", memberId).session(admin)).andExpect(status().isOk());

        MockHttpServletRequestBuilder edit = post("/admin/users/{id}/edit", memberId).session(admin)
                .param("_csrf", token).param("role", "ADMIN").param("password", "reset-password");
        mvc.perform(edit).andExpect(redirectedUrl("/admin/users"));
        member = users.findByUsername("member");
        assertThat(member.isEnabled()).isFalse();
        assertThat(member.getRole()).isEqualTo(UserAccount.Role.ADMIN);
        assertThat(encoder.matches("reset-password", member.getPasswordHash())).isTrue();
        mvc.perform(get("/todos").session(memberSession).accept("text/html")).andExpect(status().is3xxRedirection());

        mvc.perform(post("/admin/users/{id}/edit", memberId).session(admin)
                .param("_csrf", token).param("role", "ADMIN").param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/admin/users").session(login(mvc, "member", "reset-password")))
                .andExpect(status().isOk());

        long adminId = users.findByUsername("administrator").getId();
        mvc.perform(post("/admin/users/{id}/delete", adminId).session(admin).param("_csrf", token))
                .andExpect(flash().attributeExists("error"));
        assertThat(users.findById(adminId)).isNotNull();
        mvc.perform(post("/admin/users/{id}/delete", memberId).session(admin).param("_csrf", token))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findById(memberId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE user_id = ?", Integer.class, memberId))
                .isZero();
        assertThat(output.getAll())
                .contains("Service start method=UserManagementService.create(..) arguments=[String=[redacted], String=[redacted]]")
                .contains("Service success method=UserManagementService.create(..) result=void")
                .contains("Service failure method=UserManagementService.create(..) exception=IllegalArgumentException")
                .containsPattern("Service timing method=UserManagementService.create\\(\\.\\.\\) duration_us=\\d+")
                .doesNotContain("first-password", "another-password", "reset-password", member.getPasswordHash());
    }

    @Test
    void singularTodoApiUsesJsonEnvelopeAndConfiguredCors() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        String credentials = basicAuth("administrator", "admin-test-password");
        String origin = "http://localhost:5173";
        mvc.perform(get("/missing-page").header("Authorization", credentials).accept("text/html"))
                .andExpect(status().isNotFound()).andExpect(view().name("error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("404")));
        mvc.perform(get("/api/missing-endpoint").header("Authorization", credentials).accept("text/html"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(options("/api/todo").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options("/api/todo/42").header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        String payload = """
                {"title":"REST task","category":"デザイン","priority":1}
                """;
        var created = mvc.perform(post("/api/todo").header("Authorization", credentials).header("Origin", origin)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.title").value("REST task")).andReturn();
        String location = Objects.requireNonNull(created.getResponse().getHeader("Location"));
        assertThat(location).startsWith("/api/todo/");
        mvc.perform(get("/api/todo").header("Authorization", credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/todos").header("Authorization", credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get(location).header("Authorization", credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("REST task"));
        mvc.perform(put(location).header("Authorization", credentials)
                .contentType("application/json").content(payload.replace("REST task", "Updated task")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Updated task"));
        mvc.perform(post("/api/todo").header("Authorization", credentials).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").isArray());
        mvc.perform(post("/api/todo").header("Authorization", credentials).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").hasJsonPath());
        mvc.perform(get("/api/todo/invalid").header("Authorization", credentials))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(delete(location).header("Authorization", credentials))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete(location).header("Authorization", credentials))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get(location).header("Authorization", credentials))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").hasJsonPath());
    }

    @Test
    void rollsBackTodoAndHistoryButKeepsTheAuditAttempt() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        String credentials = basicAuth("administrator", "admin-test-password");
        String payload = """
                {"title":"Transaction verification","category":"デザイン","priority":1}
                """;

        var attributes = context.getBean(TransactionAttributeSource.class);
        assertThat(attributes.getTransactionAttribute(TodoService.class.getMethod("create", Todo.class),
                TodoService.class).rollbackOn(new IOException())).isTrue();
        assertThat(attributes.getTransactionAttribute(TodoService.class.getMethod("findById", Long.class),
                TodoService.class).isReadOnly()).isTrue();

        int auditsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM todo_audit_log", Integer.class);
        int historiesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM todo_history", Integer.class);
        jdbc.execute("ALTER TABLE todo_history ADD CONSTRAINT reject_create CHECK (operation <> 'CREATE') NOCHECK");
        try {
            mvc.perform(post("/api/todo").header("Authorization", credentials)
                    .contentType("application/json").content(payload))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE todo_history DROP CONSTRAINT reject_create");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE title = 'Transaction verification'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_history", Integer.class)).isEqualTo(historiesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_audit_log", Integer.class)).isEqualTo(auditsBefore + 1);

        mvc.perform(post("/api/todo").header("Authorization", credentials)
                .contentType("application/json").content(payload)).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM todos WHERE title = 'Transaction verification'", Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM todo_history h JOIN todo_audit_log a ON a.operation_id = h.operation_id
                WHERE h.todo_id = ? AND h.operation = 'CREATE' AND a.operation = 'CREATE'
                """, Integer.class, id)).isEqualTo(1);

        var admin = login(mvc, "administrator", "admin-test-password");
        var list = mvc.perform(get("/todos").session(admin)).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/todos/delete").session(admin).param("_csrf", csrf(list))
                .param("ids", String.valueOf(id), String.valueOf(id), "999999")).andExpect(redirectedUrl("/todos"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_history WHERE todo_id = ? AND operation = 'BULK_DELETE'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void managesAttachmentsAndChecksSizeAndOwnership() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        var admin = login(mvc, "administrator", "admin-test-password");
        var todosPage = mvc.perform(get("/todos").session(admin)).andExpect(status().isOk()).andReturn();
        String token = csrf(todosPage);
        mvc.perform(post("/todos").session(admin).param("_csrf", token)
                .param("title", "Attachment verification").param("priority", "1")).andExpect(redirectedUrl("/todos"));
        long id = jdbc.queryForObject("SELECT id FROM todos WHERE title = 'Attachment verification'", Long.class);
        String url = "/todos/" + id + "/attachments";
        mvc.perform(get("/todos/{id}/edit", id).session(admin)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(url)));
        mvc.perform(get(url).session(admin)).andExpect(status().isOk()).andExpect(view().name("attachments"));

        byte[] data = "attachment contents".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "folder/資料.txt", "text/plain", data);
        mvc.perform(multipart(url).file(file).session(admin)).andExpect(status().isForbidden());
        mvc.perform(multipart(url).file(file).param("_csrf", token).session(admin)).andExpect(redirectedUrl(url));
        long attachmentId = jdbc.queryForObject("SELECT id FROM todo_attachments WHERE todo_id = ?", Long.class, id);
        String name = jdbc.queryForObject("SELECT storage_name FROM todo_attachments WHERE id = ?", String.class, attachmentId);
        assertThat(java.util.UUID.fromString(name).toString()).isEqualTo(name);
        Path stored = attachmentDirectory.resolve(name);
        assertThat(stored).exists();
        var download = mvc.perform(get(url + "/" + attachmentId).session(admin))
                .andExpect(status().isOk()).andExpect(content().bytes(data)).andReturn();
        assertThat(ContentDisposition.parse(download.getResponse().getHeader("Content-Disposition")).getFilename())
                .isEqualTo("資料.txt");

        var tooLarge = new MockMultipartFile("file", "large.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]);
        mvc.perform(multipart(url).file(tooLarge).param("_csrf", token).session(admin))
                .andExpect(status().is(413));
        mvc.perform(post("/admin/users").session(admin).param("_csrf", token)
                .param("username", "attachment-member").param("password", "attachment-test-password"))
                .andExpect(redirectedUrl("/admin/users"));
        var member = login(mvc, "attachment-member", "attachment-test-password");
        mvc.perform(get(url + "/" + attachmentId).session(member)).andExpect(status().isNotFound());

        mvc.perform(post(url + "/" + attachmentId + "/delete").session(admin).param("_csrf", token))
                .andExpect(redirectedUrl(url));
        assertThat(stored).doesNotExist();
        mvc.perform(get(url + "/" + attachmentId).session(admin)).andExpect(status().isNotFound());

        mvc.perform(multipart(url).file(file).param("_csrf", token).session(admin)).andExpect(redirectedUrl(url));
        String second = jdbc.queryForObject("SELECT storage_name FROM todo_attachments WHERE todo_id = ?", String.class, id);
        mvc.perform(post("/todos/{id}/delete", id).session(admin).param("_csrf", token))
                .andExpect(redirectedUrl("/todos"));
        assertThat(attachmentDirectory.resolve(second)).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_attachments WHERE todo_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void switchesLanguagesForTheSessionAndTranslatesValidation() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"ja\"")));
        var loginPage = mvc.perform(get("/login").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"en\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Sign in</h1>"))).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginPage.getRequest().getSession(false);
        var loginResult = mvc.perform(post("/login").session(Objects.requireNonNull(session))
                .param("_csrf", csrf(loginPage)).param("username", "administrator")
                .param("password", "admin-test-password")).andExpect(redirectedUrl("/")).andReturn();
        session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        mvc.perform(get("/todos").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(">ToDo list</h1>")));
        mvc.perform(get("/calendar").param("year", "2026").param("month", "4").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(">April 2026</h1>")));
        mvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(">User management</h1>")));
        var create = mvc.perform(get("/todos/new").session(session).param("lang", "fr"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"en\""))).andReturn();
        mvc.perform(post("/todos/confirm").session(session).param("_csrf", csrf(create)).param("title", ""))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a task.")));
        mvc.perform(post("/todos/confirm").session(session).param("_csrf", csrf(create))
                .param("title", "Draft").param("category", "デザイン").param("priority", "1"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(">Confirm new ToDo</h1>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Design</span>")));
        mvc.perform(post("/api/todo").session(session).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Check the information you entered."))
                .andExpect(jsonPath("$.data[?(@.field == 'title')].message").value(org.hamcrest.Matchers.hasItem("Enter a task.")));

        mvc.perform(get("/todos").session(session).param("lang", "ja"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(">一覧</h1>")));
        mvc.perform(post("/api/todo").session(session).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("入力に誤りがあります"))
                .andExpect(jsonPath("$.data[?(@.field == 'title')].message").value(org.hamcrest.Matchers.hasItem("やることを入力してください")));
    }

    @Test
    @Transactional
    void todoControllerRendersStoredDetailsAndValidatesTheEditForm() throws Exception {
        jdbc.update("INSERT INTO todos (title, category, priority, due_date) VALUES (?, ?, ?, ?)",
                "Transactional controller fixture", "デザイン", 1, java.sql.Date.valueOf("2026-05-03"));
        long id = jdbc.queryForObject("SELECT id FROM todos WHERE title = 'Transactional controller fixture'", Long.class);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        var admin = login(mvc, "administrator", "admin-test-password");

        var edit = mvc.perform(get("/todos/{id}/edit", id).session(admin))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(model().attribute("todo", org.hamcrest.Matchers.hasProperty(
                        "title", org.hamcrest.Matchers.is("Transactional controller fixture"))))
                .andReturn();
        mvc.perform(post("/todos/{id}/confirm", id).session(admin).param("_csrf", csrf(edit))
                .param("title", "").param("category", "デザイン").param("priority", "1"))
                .andExpect(status().isOk()).andExpect(view().name("edit"))
                .andExpect(model().attributeHasFieldErrors("todo", "title"));
        mvc.perform(post("/todos/{id}/confirm", id).session(admin).param("_csrf", csrf(edit))
                .param("title", "Valid draft").param("category", "デザイン").param("priority", "1"))
                .andExpect(status().isOk()).andExpect(view().name("edit-confirm"));
    }

    @AfterTransaction
    void verifiesControllerFixtureWasRolledBack() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE title = 'Transactional controller fixture'",
                Integer.class)).isZero();
    }

    private void assertMcpTodoScope(MockMvc mvc, String credentials) throws Exception {
        var initialize = mvc.perform(mcpRequest(credentials).content("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"test","version":"1"}}}
                """)).andExpect(status().isOk()).andReturn();
        String sessionId = Objects.requireNonNull(initialize.getResponse().getHeader("Mcp-Session-Id"));
        mvc.perform(mcpRequest(credentials).header("Mcp-Session-Id", sessionId).content("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """)).andExpect(status().isAccepted());
        var result = mvc.perform(mcpRequest(credentials).header("Mcp-Session-Id", sessionId).content("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_todos","arguments":{}}}
                """)).andExpect(status().isOk()).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }
        assertThat(result.getResponse().getContentAsString())
                .contains("Member API task").doesNotContain("Admin task");
    }

    private MockHttpServletRequestBuilder mcpRequest(String credentials) {
        return post("/mcp").header("Authorization", credentials)
                .contentType("application/json").accept("application/json", "text/event-stream");
    }

    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private org.springframework.mock.web.MockHttpSession login(MockMvc mvc, String username, String password)
            throws Exception {
        var loginPage = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginPage.getRequest().getSession(false);
        var result = mvc.perform(post("/login").session(Objects.requireNonNull(session))
                .param("_csrf", csrf(loginPage)).param("username", username).param("password", password))
                .andExpect(redirectedUrl("/")).andReturn();
        return (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    }

    private String csrf(MvcResult result) {
        return ((org.springframework.security.web.csrf.CsrfToken) result.getRequest().getAttribute("_csrf")).getToken();
    }
}
