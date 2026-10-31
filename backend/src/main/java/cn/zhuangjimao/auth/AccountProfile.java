package cn.zhuangjimao.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountProfile {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;

    AccountProfile(AccountMapper accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public Account.Summary nickname(AccountPrincipal principal, String nickname) {
        if (nickname == null) throw AccountInputs.invalid("请填写昵称。");
        var account = lockCurrent(principal);
        accounts.updateNickname(account.id(), AccountInputs.nickname(nickname, account.username()));
        return accounts.byId(account.id()).summary();
    }

    @Transactional
    public void password(AccountPrincipal principal, String oldPassword, String newPassword, String confirmPassword) {
        AccountInputs.password(oldPassword);
        AccountInputs.password(newPassword);
        if (!newPassword.equals(confirmPassword)) throw AccountInputs.invalid("两次输入的新密码不一致。");
        var account = lockCurrent(principal);
        if (!passwords.matches(oldPassword, account.passwordHash())) {
            throw new AuthException(400, "CURRENT_PASSWORD_INCORRECT", "旧密码不正确，请重试。");
        }
        accounts.updatePassword(account.id(), passwords.encode(newPassword));
    }

    private Account lockCurrent(AccountPrincipal principal) {
        var account = accounts.lockById(principal.accountId());
        if (!principal.matches(account)) throw new AuthException(401, "UNAUTHENTICATED", "登录已过期，请重新登录。");
        return account;
    }
}
