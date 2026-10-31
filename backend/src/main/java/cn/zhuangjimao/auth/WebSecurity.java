package cn.zhuangjimao.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

@Configuration
class WebSecurity {
    @Bean
    java.time.Clock clock() { return java.time.Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }

    @Bean
    HttpSessionSecurityContextRepository securityContexts() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    AuthenticationManager authenticationManager(AccountMapper accounts, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(username -> {
            var account = accounts.byUsername(username);
            if (account == null) throw new UsernameNotFoundException("Invalid credentials");
            return new AccountPrincipal(account);
        });
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain browserSecurity(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
                                       HttpSessionCsrfTokenRepository csrfTokens, AccountMapper accounts) throws Exception {
        return http.securityContext(context -> context.securityContextRepository(contexts))
                .addFilterAfter(new AccountSessionFilter(accounts), org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/images/**", "/favicon.svg",
                            "/api/products", "/api/products/*", "/api/auth/csrf").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                            "/api/compatibility/check", "/api/recommendations").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/auth/me", "/api/account/**", "/api/cart/**", "/api/orders/**").authenticated()
                    .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, auth) -> json(response, 200, "LOGGED_OUT", "已退出登录。")))
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, exception) -> json(response, 401, "UNAUTHENTICATED", "登录已过期或尚未登录，请重新登录。"))
                    .accessDeniedHandler((request, response, exception) -> {
                        boolean privateRequest = request.getRequestURI().matches("/api/(admin|account|cart|orders)(/.*)?");
                        if (privateRequest && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null) {
                            json(response, 401, "UNAUTHENTICATED", "登录已过期或尚未登录，请重新登录。");
                        } else if (exception instanceof CsrfException) json(response, 403, "CSRF_INVALID", "页面安全凭证已失效，请重试操作。");
                        else json(response, 403, "FORBIDDEN", "没有管理员权限，无法访问此功能。");
                    }))
                .build();
    }

    static void json(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
