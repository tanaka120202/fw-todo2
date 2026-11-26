package com.example.todoapp2;

import lombok.Data;

@Data
public class UserAccount {
    private long id;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean enabled;
    private long version;

    public enum Role { USER, ADMIN }
}
