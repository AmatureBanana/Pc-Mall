package cn.zhuangjimao.auth;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Public HTTP seam, backed by the guarded, real pc_mall_test schema. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mall.login-limit.max-failures=3", "mall.login-limit.window=5m", "mall.login-limit.lock-duration=2m", "mall.login-limit.max-keys=16"})
@org.springframework.context.annotation.Import(SessionAuthTest.TimeConfiguration.class)
@ActiveProfiles("test")
class SessionAuthTest {
    static final String ADMIN = "itadm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    static final String ADMIN_PASSWORD = UUID.randomUUID().toString();
    @DynamicPropertySource
    static void bootstrap(DynamicPropertyRegistry properties) {
        properties.add("mall.bootstrap.username", () -> ADMIN);
        properties.add("mall.bootstrap.password", () -> ADMIN_PASSWORD);
    }
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate fixture;
    @org.springframework.beans.factory.annotation.Autowired TestClock time;
    final ObjectMapper json = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void moveBeyondEarlierRateLimits() { time.advance(java.time.Duration.ofMinutes(10)); }

    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        TestClock testClock() { return new TestClock(); }
    }

    static class TestClock extends java.time.Clock {
        private final java.util.concurrent.atomic.AtomicReference<java.time.Instant> instant =
                new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-31T00:00:00Z"));
        void advance(java.time.Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public java.time.Instant instant() { return instant.get(); }
    }

    @org.junit.jupiter.api.AfterAll
    static void removeAdministrator(@org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate fixture) {
        fixture.update("DELETE FROM account WHERE username = ? AND role = 'ADMIN'", ADMIN);
    }

    @Test
    void registrationCreatesOnlyAnOrdinaryUserAndRequiresSeparateLogin() throws Exception {
        var browser = browser();
        String username = username();
        var response = post(browser, "/api/auth/register", registration(username, "Password123!"));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).doesNotContain("password", "Password123!");
        assertThat(get(browser, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(post(browser, "/api/auth/register", registration(username.toUpperCase(), "Password123!"))
                .statusCode()).isEqualTo(409);
    }

    @Test
    void simultaneousCaseVariantRegistrationsCreateExactlyOneAccount() throws Exception {
        String name = username();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (String variant : java.util.List.of(name, name.toUpperCase())) {
                results.add(executor.submit(() -> { start.await(); return post(browser(), "/api/auth/register", registration(variant, "Password123!")).statusCode(); }));
            }
            start.countDown();
            assertThat(java.util.List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
    }

    static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    @Test
    void loginRotatesSessionAndCsrfAndLogoutRevokesTheOldCookie() throws Exception {
        var browser = browser();
        String name = username();
        assertThat(post(browser, "/api/auth/register", registration(name, "Password123!")).statusCode()).isEqualTo(201);
        String oldCookie = cookie(browser);
        String oldToken = json.readTree(get(browser, "/api/auth/csrf").body()).path("data").path("token").asText();
        assertThat(post(browser, "/api/auth/login", Map.of("username", name.toUpperCase(), "password", "Password123!"))
                .statusCode()).isEqualTo(200);
        assertThat(cookie(browser)).isNotEqualTo(oldCookie);
        var me = get(browser, "/api/auth/me?user_id=1");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).path("data").path("username").asText()).isEqualTo(name);
        assertThat(json.readTree(me.body()).path("data").path("role").asText()).isEqualTo("USER");
        assertThat(me.body()).doesNotContain("password", "Password123!");
        assertThat(get(browser, "/api/admin/overview").statusCode()).isEqualTo(403);
        var missingCsrf = browser.send(HttpRequest.newBuilder(uri("/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        var staleCsrf = browser.send(HttpRequest.newBuilder(uri("/api/auth/logout")).header("X-CSRF-TOKEN", oldToken)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(staleCsrf.statusCode()).isEqualTo(403);
        String loggedInCookie = cookie(browser);
        assertThat(post(browser, "/api/auth/logout", Map.of()).statusCode()).isEqualTo(200);
        assertThat(get(browser, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/auth/me"))
                .header("Cookie", loggedInCookie).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    String cookie(HttpClient browser) {
        return ((CookieManager) browser.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(cookie -> "JSESSIONID=" + cookie.getValue()).findFirst().orElseThrow();
    }

    @Test
    void initializedAdministratorUsesTheSameLoginAndHasNavigationWhileGuestsAreDenied() throws Exception {
        var browser = browser();
        assertThat(get(browser, "/api/products").statusCode()).isEqualTo(200);
        for (String path : java.util.List.of("/api/auth/me", "/api/cart", "/api/orders", "/api/admin/overview")) {
            assertThat(get(browser, path).statusCode()).as(path).isEqualTo(401);
        }
        assertThat(post(browser, "/api/auth/login", Map.of("username", ADMIN, "password", ADMIN_PASSWORD)).statusCode()).isEqualTo(200);
        var overview = get(browser, "/api/admin/overview");
        assertThat(overview.statusCode()).isEqualTo(200);
        assertThat(overview.body()).contains("商品管理", "用户管理", "订单管理").doesNotContain(ADMIN_PASSWORD, "passwordHash");
        // Repeated startup with different supplied credentials must not reset the existing administrator.
        var application = new org.springframework.boot.SpringApplication(MallApplication.class);
        application.setAdditionalProfiles("test");
        application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("bootstrap-restart", Map.of(
                        "mall.bootstrap.username", ADMIN, "mall.bootstrap.password", "DifferentPassword!"))));
        try (var restarted = application.run("--server.port=0")) {
            int secondPort = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) restarted).getWebServer().getPort();
            var client = browser();
            String token = json.readTree(client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + secondPort + "/api/auth/csrf"))
                    .build(), HttpResponse.BodyHandlers.ofString()).body()).path("data").path("token").asText();
            var originalPassword = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + secondPort + "/api/auth/login"))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(originalPassword.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void malformedLoginAndMissingCsrfNeverAuthenticate() throws Exception {
        var browser = browser();
        for (var invalid : java.util.List.of(Map.of(), Map.of("username", ADMIN, "password", "short"),
                Map.of("username", ADMIN, "password", ADMIN_PASSWORD, "user_id", "1"),
                Map.of("username", ADMIN, "password", ADMIN_PASSWORD, "role", "ADMIN"))) {
            assertThat(post(browser, "/api/auth/login", invalid).statusCode()).isEqualTo(400);
        }
        var noCsrf = browser.send(HttpRequest.newBuilder(uri("/api/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(get(browser, "/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void incorrectCredentialsHaveUniformFeedbackAndDisabledSessionsLoseAccess() throws Exception {
        var browser = browser();
        String name = username();
        assertThat(post(browser, "/api/auth/register", registration(name, "Password123!")).statusCode()).isEqualTo(201);
        var wrong = post(browser, "/api/auth/login", Map.of("username", name, "password", "WrongPassword!"));
        var absent = post(browser, "/api/auth/login", Map.of("username", username(), "password", "WrongPassword!"));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(absent.statusCode()).isEqualTo(401);
        assertThat(absent.body()).isEqualTo(wrong.body());
        assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", "Password123!")).statusCode()).isEqualTo(200);
        try {
            fixture.update("UPDATE account SET status = 'DISABLED' WHERE username = ?", name);
            assertThat(get(browser, "/api/auth/me").statusCode()).isEqualTo(401);
            assertThat(get(browser, "/api/products").statusCode()).isEqualTo(200);
            assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", "Password123!")).statusCode()).isEqualTo(401);
        } finally { fixture.update("UPDATE account SET status = 'ACTIVE' WHERE username = ?", name); }
    }

    @Test
    void configurableLoginLimitCoversCaseVariantsAndIpRotationOfUsernamesThenExpires() throws Exception {
        var browser = browser();
        String name = username();
        for (int i = 0; i < 3; i++) {
            assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", "WrongPassword!"))
                    .statusCode()).isEqualTo(401);
        }
        var limited = post(browser, "/api/auth/login", Map.of("username", name.toUpperCase(), "password", "WrongPassword!"));
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.body()).contains("LOGIN_RATE_LIMITED");
        assertThat(post(browser, "/api/auth/login", Map.of("username", username(), "password", "WrongPassword!"))
                .statusCode()).isEqualTo(429);
        time.advance(java.time.Duration.ofMinutes(2));
        assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", "WrongPassword!"))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void blockedPeerCannotFillTheLimiterAndPreventAnotherPeerFromLoggingIn() throws Exception {
        var blocked = browser();
        String validUser = username();
        assertThat(post(blocked, "/api/auth/register", registration(validUser, "Password123!")).statusCode()).isEqualTo(201);
        String wrongUser = username();
        for (int i = 0; i < 3; i++) {
            assertThat(post(blocked, "/api/auth/login", Map.of("username", wrongUser, "password", "WrongPassword!"))
                    .statusCode()).isEqualTo(401);
        }
        for (int i = 0; i < 20; i++) {
            assertThat(post(blocked, "/api/auth/login", Map.of("username", username(), "password", "WrongPassword!"))
                    .statusCode()).isEqualTo(429);
        }
        var otherPeer = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .localAddress(java.net.InetAddress.getByName("127.0.0.2")).build();
        assertThat(post(otherPeer, "/api/auth/login", Map.of("username", validUser, "password", "Password123!"))
                .statusCode()).isEqualTo(200);
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext server;

    @Test
    void sessionCookieIsLocalHttpSafeAndExpiredSessionsCannotReadOrWritePrivateResources() throws Exception {
        var browser = browser();
        var csrf = get(browser, "/api/auth/csrf");
        assertThat(csrf.headers().firstValue("Set-Cookie").orElseThrow()).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        String name = username();
        assertThat(post(browser, "/api/auth/register", registration(name, "Password123!")).statusCode()).isEqualTo(201);
        assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", "Password123!")).statusCode()).isEqualTo(200);
        var tomcat = ((org.springframework.boot.web.embedded.tomcat.TomcatWebServer) server.getWebServer()).getTomcat();
        var context = (org.apache.catalina.Context) tomcat.getHost().findChildren()[0];
        var session = context.getManager().findSession(cookie(browser).substring("JSESSIONID=".length()));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(7200);
        // Lifecycle fixture: move last access into the past; observe only the subsequent public HTTP result.
        session.setCreationTime(System.currentTimeMillis() - 7_201_000);
        assertThat(get(browser, "/api/auth/me").statusCode()).isEqualTo(401);
        var write = browser.send(HttpRequest.newBuilder(uri("/api/orders"))
                .POST(HttpRequest.BodyPublishers.ofString("{}" )).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(write.statusCode()).isEqualTo(401);
    }

    @Test
    void registrationRejectsInvalidFieldsAndCannotAcceptAnAdministratorRole() throws Exception {
        var browser = browser();
        for (var invalid : java.util.List.of(
                registration("abc", "Password123!"), registration("a".repeat(21), "Password123!"),
                registration("bad-name", "Password123!"), registration(username(), "short"),
                registration(username(), "a".repeat(65)), registration(username(), "密".repeat(25)),
                Map.of("username", username(), "password", "Password123!", "confirmPassword", "different"),
                Map.of("username", username(), "password", "Password123!", "confirmPassword", "Password123!", "nickname", "名".repeat(31)),
                Map.of("username", username(), "password", "Password123!", "confirmPassword", "Password123!", "role", "ADMIN"),
                Map.of("username", username(), "password", "Password123!", "confirmPassword", "Password123!", "user_id", "1"),
                Map.of("username", 123, "password", "Password123!", "confirmPassword", "Password123!"), Map.of())) {
            var response = post(browser, "/api/auth/register", invalid);
            assertThat(response.statusCode()).as("invalid registration: %s", invalid.keySet()).isEqualTo(400);
            assertThat(response.body()).contains("INVALID_INPUT");
        }
        assertThat(post(browser, "/api/auth/register", registration(username(), "密".repeat(24))).statusCode()).isEqualTo(201);
        assertThat(post(browser, "/api/auth/register", registration(username(), "a".repeat(64))).statusCode()).isEqualTo(201);
    }

    static String username() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 15); }

    static Map<String, String> registration(String username, String password) {
        return Map.of("username", username, "password", password, "confirmPassword", password, "nickname", "测试用户");
    }

    HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(HttpClient browser, String path, Object body) throws Exception {
        var csrf = get(browser, "/api/auth/csrf");
        assertThat(csrf.statusCode()).isEqualTo(200);
        JsonNode token = json.readTree(csrf.body()).path("data");
        return browser.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
