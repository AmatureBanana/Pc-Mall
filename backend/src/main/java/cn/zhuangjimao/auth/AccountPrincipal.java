package cn.zhuangjimao.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Retains the version actually used for authentication; User erases its password after login.
 * Public so owner-scoped services can read the session identity without trusting a request field;
 * everything else about it stays internal to this package.
 */
public final class AccountPrincipal extends User {
    private final long accountId;
    private final long credentialVersion;

    AccountPrincipal(Account account) {
        super(account.username(), account.passwordHash(), account.status().equals("ACTIVE"), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        accountId = account.id();
        credentialVersion = account.credentialVersion();
    }

    /** The authenticated owner id, for services that must scope rows to the current session. */
    public long ownerId() { return accountId; }

    /** Same value, package-internal: existing account services keep reading it directly. */
    long accountId() { return accountId; }

    boolean matches(Account account) {
        return account != null && account.id() == accountId && account.status().equals("ACTIVE")
                && account.credentialVersion() == credentialVersion;
    }
}
