package cn.zhuangjimao.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final LoginAuthentication authentication;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrfTokens;

    AuthController(AccountMapper accounts, PasswordEncoder passwords, LoginAuthentication authentication,
                   HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrfTokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.authentication = authentication;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/csrf")
    Map<String, Object> csrf(CsrfToken token) { return Map.of("data", token); }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        AccountInputs.username(input.username());
        AccountInputs.password(input.password());
        Authentication authenticated = authentication.authenticate(input.username(), input.password(), request.getRemoteAddr());
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authenticated, request, response);
        new CsrfAuthenticationStrategy(csrfTokens).onAuthentication(authenticated, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return Map.of("data", accounts.byUsername(authenticated.getName()).summary());
    }

    @GetMapping("/me")
    Map<String, Object> me(Authentication authentication) {
        return Map.of("data", accounts.byUsername(authentication.getName()).summary());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@RequestBody Registration input) {
        AccountInputs.username(input.username());
        AccountInputs.password(input.password());
        if (!input.password().equals(input.confirmPassword())) throw AccountInputs.invalid("两次输入的密码不一致。");
        String nickname = AccountInputs.nickname(input.nickname(), input.username());
        if (accounts.byUsername(input.username()) != null) throw duplicateUsername();
        try {
            accounts.insert(input.username(), passwords.encode(input.password()), nickname, "USER");
        } catch (DuplicateKeyException error) {
            throw duplicateUsername();
        }
        return Map.of("message", "注册成功，请登录。");
    }

    private AuthException duplicateUsername() { return new AuthException(409, "USERNAME_TAKEN", "用户名已存在，请更换用户名。"); }
    record Registration(String username, String password, String confirmPassword, String nickname) {}
    record Login(String username, String password) {}
}
