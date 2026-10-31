package cn.zhuangjimao.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/** Retains the version actually used for authentication; User erases its password after login. */
final class AccountPrincipal extends User {
    private final long accountId;
    private final long credentialVersion;

    AccountPrincipal(Account account) {
        super(account.username(), account.passwordHash(), account.status().equals("ACTIVE"), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        accountId = account.id();
        credentialVersion = account.credentialVersion();
    }

    long accountId() { return accountId; }

    boolean matches(Account account) {
        return account != null && account.id() == accountId && account.status().equals("ACTIVE")
                && account.credentialVersion() == credentialVersion;
    }
}
