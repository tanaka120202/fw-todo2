package com.example.todoapp2;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private final long id;
    private final long version;
    private final boolean admin;

    public AccountPrincipal(UserAccount account) {
        super(account.getUsername(), account.getPasswordHash(), account.isEnabled(), true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_" + account.getRole()));
        id = account.getId();
        version = account.getVersion();
        admin = account.getRole() == UserAccount.Role.ADMIN;
    }

    public long getId() { return id; }
    public long getVersion() { return version; }
    public boolean isAdmin() { return admin; }
}
