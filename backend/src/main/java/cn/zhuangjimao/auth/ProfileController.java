package cn.zhuangjimao.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
class ProfileController {
    private final AccountMapper accounts;
    private final AccountProfile profiles;

    ProfileController(AccountMapper accounts, AccountProfile profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    Map<String, Object> profile(Authentication authentication) {
        return Map.of("data", accounts.byUsername(authentication.getName()).summary());
    }

    @PostMapping("/profile")
    Map<String, Object> nickname(@AuthenticationPrincipal AccountPrincipal principal, @RequestBody Nickname input) {
        return Map.of("data", profiles.nickname(principal, input.nickname()));
    }

    @PostMapping("/password")
    Map<String, String> password(@AuthenticationPrincipal AccountPrincipal principal, @RequestBody PasswordChange input,
                                HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        profiles.password(principal, input.oldPassword(), input.newPassword(), input.confirmPassword());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return Map.of("message", "密码修改成功，旧会话已失效，请重新登录。");
    }

    record Nickname(String nickname) {}
    record PasswordChange(String oldPassword, String newPassword, String confirmPassword) {}
}
