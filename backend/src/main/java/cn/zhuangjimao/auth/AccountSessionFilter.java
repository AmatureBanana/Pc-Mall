package cn.zhuangjimao.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Re-check account state and credentials so disabled accounts and old sessions lose access. */
final class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountMapper accounts;
    AccountSessionFilter(AccountMapper accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Account account;
            try { account = accounts.byUsername(auth.getName()); }
            catch (DataAccessException error) {
                WebSecurity.json(response, 503, "AUTH_UNAVAILABLE", "账户服务暂时不可用，请稍后重试。");
                return;
            }
            if (!(auth.getPrincipal() instanceof AccountPrincipal principal) || !principal.matches(account)) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
