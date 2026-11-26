package com.example.todoapp2;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementService {
    private final UserMapper users;
    private final BCryptPasswordEncoder encoder;
    private final TodoAttachmentService attachments;

    public UserManagementService(UserMapper users, BCryptPasswordEncoder encoder, TodoAttachmentService attachments) {
        this.users = users;
        this.encoder = encoder;
        this.attachments = attachments;
    }

    public List<UserAccount> list() {
        return users.findAll();
    }

    public UserAccount find(long id) {
        UserAccount account = users.findById(id);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return account;
    }

    public void create(String username, String password) {
        register(username, password);
    }

    public void edit(long id, UserAccount.Role role, boolean enabled, String password) {
        String hash = password.isEmpty() ? null : hash(encoder, password);
        if (users.update(id, role, enabled, hash) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public void delete(long id, AccountPrincipal actor) {
        if (id == actor.getId()) {
            throw new IllegalArgumentException("user.error.self");
        }
        if (users.lockById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        attachments.deleteFilesAfterCommit(users.lockOwnedTodoIds(id));
        if (users.delete(id, actor.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private void register(String username, String password) {
        String name = username.strip();
        if (name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("user.error.name");
        }
        String hash = hash(encoder, password);
        try {
            users.insert(name, hash, UserAccount.Role.USER);
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalArgumentException("user.error.duplicate");
        }
    }

    static String hash(BCryptPasswordEncoder encoder, String password) {
        if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("user.error.password");
        }
        return encoder.encode(password);
    }

}
