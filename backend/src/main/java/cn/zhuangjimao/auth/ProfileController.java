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
    private final AccountAvatar avatars;

    ProfileController(AccountMapper accounts, AccountProfile profiles, AccountAvatar avatars) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.avatars = avatars;
    }

    @GetMapping("/avatar")
    org.springframework.http.ResponseEntity<byte[]> avatar(@AuthenticationPrincipal AccountPrincipal principal) {
        byte[] image = avatars.read(principal.ownerId());
        String type = "image/png";
        if (image == null) {
            type = "image/svg+xml";
            image = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><circle cx='50' cy='50' r='50' fill='#91a6cc'/><circle cx='50' cy='35' r='17' fill='white'/><path d='M20 84v-8a30 30 0 0 1 60 0v8z' fill='white'/></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").contentType(org.springframework.http.MediaType.parseMediaType(type)).body(image);
    }

    @PostMapping("/avatar")
    Map<String, Object> avatar(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) throws java.io.IOException {
        return Map.of("data", avatars.upload(principal, request.getInputStream().readNBytes(2 * 1024 * 1024 + 1)));
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
