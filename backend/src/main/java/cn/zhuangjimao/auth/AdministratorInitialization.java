package cn.zhuangjimao.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class AdministratorInitialization implements ApplicationRunner {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    private final TransactionTemplate transactions;

    AdministratorInitialization(AccountMapper accounts, PasswordEncoder passwords,
                                @Value("${mall.bootstrap.username:}") String username,
                                @Value("${mall.bootstrap.password:}") String password,
                                PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (username.isEmpty() && password.isEmpty()) return;
        AccountInputs.username(username);
        AccountInputs.password(password);
        transactions.executeWithoutResult(transaction -> {
            accounts.lockInitialization();
            if (accounts.administrators() > 0) return;
            if (accounts.byUsername(username) != null) {
                throw new IllegalStateException("Administrator initialization refuses to promote an existing account.");
            }
            accounts.insert(username, passwords.encode(password), "商城管理员", "ADMIN");
        });
    }
}
