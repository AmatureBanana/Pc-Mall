package cn.zhuangjimao.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded, process-local failure limits by account and by actual peer address. */
@Component
class LoginAuthentication {
    private final AuthenticationManager authentication;
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockDuration;
    private final int maxKeys;
    private final Map<String, Bucket> buckets = new HashMap<>();

    LoginAuthentication(AuthenticationManager authentication, Clock clock,
                        @Value("${mall.login-limit.max-failures:5}") int maxFailures,
                        @Value("${mall.login-limit.window:15m}") Duration window,
                        @Value("${mall.login-limit.lock-duration:15m}") Duration lockDuration,
                        @Value("${mall.login-limit.max-keys:10000}") int maxKeys) {
        if (maxKeys < 2 || maxFailures < 1 || window.isNegative() || window.isZero() || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Login limit values must be positive.");
        }
        this.authentication = authentication;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockDuration = lockDuration;
        this.maxKeys = maxKeys;
    }

    Authentication authenticate(String username, String password, String address) {
        var permit = reserve(username, address);
        boolean failed = false;
        boolean succeeded = false;
        try {
            var result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            succeeded = true;
            return result;
        } catch (InternalAuthenticationServiceException error) {
            throw new AuthException(503, "AUTH_UNAVAILABLE", "账户服务暂时不可用，请稍后重试。");
        } catch (AuthenticationException error) {
            failed = true;
            throw new AuthException(401, "INVALID_CREDENTIALS", "用户名或密码错误，或账户不可用。");
        } finally { finish(permit, failed, succeeded); }
    }

    private synchronized Permit reserve(String username, String address) {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> bucket.inFlight == 0 && (bucket.blockedUntil.equals(Instant.MIN)
                ? !now.isBefore(bucket.expires) : !now.isBefore(bucket.blockedUntil)));
        String accountKey = "user:" + username.toLowerCase(Locale.ROOT);
        String peerKey = "ip:" + address;
        var keys = List.of(accountKey, peerKey);
        // Reject already blocked peers/accounts before allocating any attacker-controlled key.
        for (String key : keys) {
            var bucket = buckets.get(key);
            if (bucket != null && (now.isBefore(bucket.blockedUntil) || bucket.failures + bucket.inFlight >= maxFailures)) throw limited();
        }
        if (buckets.size() + keys.stream().filter(key -> !buckets.containsKey(key)).count() > maxKeys) throw limited();
        var permit = new Permit(
                buckets.computeIfAbsent(accountKey, ignored -> new Bucket(now.plus(window))),
                buckets.computeIfAbsent(peerKey, ignored -> new Bucket(now.plus(window))));
        permit.buckets().forEach(bucket -> bucket.inFlight++);
        return permit;
    }

    private synchronized void finish(Permit permit, boolean failed, boolean succeeded) {
        Instant now = clock.instant();
        for (var bucket : permit.buckets()) {
            bucket.inFlight--;
            if (failed && ++bucket.failures >= maxFailures) bucket.blockedUntil = now.plus(lockDuration);
        }
        // A valid password resets that account's failures; it cannot erase the peer's failures.
        if (succeeded) {
            permit.account().failures = 0;
            permit.account().blockedUntil = Instant.MIN;
        }
    }

    private AuthException limited() { return new AuthException(429, "LOGIN_RATE_LIMITED", "登录尝试过多，请稍后再试。"); }

    private record Permit(Bucket account, Bucket peer) {
        List<Bucket> buckets() { return List.of(account, peer); }
    }

    private static final class Bucket {
        final Instant expires;
        Instant blockedUntil = Instant.MIN;
        int failures;
        int inFlight;
        Bucket(Instant expires) { this.expires = expires; }
    }
}
