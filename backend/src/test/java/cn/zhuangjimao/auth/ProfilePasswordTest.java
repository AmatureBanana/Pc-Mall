package cn.zhuangjimao.auth;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Account behavior is observed only through HTTP against the guarded, real MySQL test schema. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mall.login-limit.max-failures=100")
@ActiveProfiles("test")
class ProfilePasswordTest {
    @LocalServerPort int port;
    final ObjectMapper json = new ObjectMapper().enable(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature());
    static final String PASSWORD = "OriginalPassword123!";

    @Test
    void profileExposesOnlyTheSessionOwnersNecessaryFields() throws Exception {
        var first = browser();
        var second = browser();
        String firstName = register(first);
        String secondName = register(second);
        login(first, firstName, PASSWORD);
        login(second, secondName, PASSWORD);
        var other = data(get(second, "/api/account/profile"));
        var response = get(first, "/api/account/profile?user_id=" + other.path("id").asLong());
        assertThat(response.statusCode()).isEqualTo(200);
        var profile = data(response);
        assertThat(profile.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "username", "nickname", "role");
        assertThat(profile.path("id").asLong()).isPositive().isNotEqualTo(other.path("id").asLong());
        assertThat(profile.path("username").asText()).isEqualTo(firstName);
        assertThat(profile.path("nickname").asText()).isEqualTo("测试用户");
        assertThat(profile.path("role").asText()).isEqualTo("USER");
        assertThat(get(browser(), "/api/account/profile").statusCode()).isEqualTo(401);
    }

    @Test
    void nicknameUpdatesPersistOnlyForTheOwnerAndEnforceUnicodeBoundariesAndProtectedFields() throws Exception {
        var owner = browser();
        var other = browser();
        String name = register(owner);
        String otherName = register(other);
        login(owner, name, PASSWORD);
        login(other, otherName, PASSWORD);
        var original = data(get(owner, "/api/account/profile"));
        for (String nickname : java.util.List.of("新昵称", "名".repeat(30), "😀".repeat(30))) {
            var response = post(owner, "/api/account/profile", Map.of("nickname", nickname));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(data(get(owner, "/api/account/profile")).path("nickname").asText()).isEqualTo(nickname);
            assertThat(data(get(owner, "/api/auth/me")).path("nickname").asText()).isEqualTo(nickname);
        }
        for (Object invalid : java.util.List.of(Map.of("nickname", "名".repeat(31)), Map.of("nickname", "😀".repeat(31)),
                Map.of("nickname", "\uD800"), Map.of("nickname", 123), Map.of())) {
            assertThat(post(owner, "/api/account/profile", invalid).statusCode()).isEqualTo(400);
        }
        for (String field : java.util.List.of("id", "user_id", "username", "role", "status", "passwordHash")) {
            assertThat(post(owner, "/api/account/profile", Map.of("nickname", "篡改", field, "1")).statusCode()).isEqualTo(400);
        }
        assertThat(post(owner, "/api/account/" + original.path("id").asLong() + "/profile", Map.of("nickname", "篡改"))
                .statusCode()).isNotEqualTo(200);
        assertThat(send(owner, "/api/account/profile", Map.of("nickname", "篡改"), null).statusCode()).isEqualTo(403);
        assertThat(post(browser(), "/api/account/profile", Map.of("nickname", "篡改")).statusCode()).isEqualTo(401);
        assertThat(data(get(owner, "/api/account/profile")).path("nickname").asText()).isEqualTo("😀".repeat(30));
        assertThat(data(get(other, "/api/account/profile")).path("nickname").asText()).isEqualTo("测试用户");
        var fresh = browser();
        login(fresh, name, PASSWORD);
        var persisted = data(get(fresh, "/api/account/profile"));
        assertThat(persisted.path("id")).isEqualTo(original.path("id"));
        assertThat(persisted.path("username").asText()).isEqualTo(name);
        assertThat(persisted.path("role").asText()).isEqualTo("USER");
        assertThat(persisted.path("nickname").asText()).isEqualTo("😀".repeat(30));
    }

    HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }

    @Test
    void changingPasswordChecksTheOldSecretAndRevokesAllOldSessionsWithoutAffectingOtherAccounts() throws Exception {
        var first = browser();
        var second = browser();
        var other = browser();
        String name = register(first);
        String otherName = register(other);
        login(first, name, PASSWORD);
        login(second, name, PASSWORD);
        login(other, otherName, PASSWORD);
        String next = "ChangedPassword456!";
        var incorrect = post(first, "/api/account/password", change("IncorrectPassword!", next));
        assertThat(incorrect.statusCode()).isEqualTo(400);
        assertThat(incorrect.body()).contains("CURRENT_PASSWORD_INCORRECT");
        assertThat(get(first, "/api/account/profile").statusCode()).isEqualTo(200);
        assertThat(get(second, "/api/account/profile").statusCode()).isEqualTo(200);
        assertThat(send(first, "/api/account/password", change(PASSWORD, next), null).statusCode()).isEqualTo(403);
        assertThat(post(browser(), "/api/account/password", change(PASSWORD, next)).statusCode()).isEqualTo(401);
        String staleToken = data(get(second, "/api/auth/csrf")).path("token").asText();
        var response = post(first, "/api/account/password", change(PASSWORD, next));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain(PASSWORD, next, "passwordHash", "credentialVersion");
        // The first request on each old session must fail, including a write with its formerly valid CSRF token.
        assertThat(send(second, "/api/account/profile", Map.of("nickname", "不应保存"), staleToken).statusCode()).isEqualTo(401);
        assertThat(get(first, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(get(second, "/api/account/profile").statusCode()).isEqualTo(401);
        assertThat(get(other, "/api/account/profile").statusCode()).isEqualTo(200);
        assertThat(post(browser(), "/api/auth/login", Map.of("username", name, "password", PASSWORD)).statusCode()).isEqualTo(401);
        var fresh = browser();
        login(fresh, name, next);
        assertThat(data(get(fresh, "/api/account/profile")).path("nickname").asText()).isEqualTo("测试用户");
    }

    static Map<String, String> change(String oldPassword, String newPassword) {
        return Map.of("oldPassword", oldPassword, "newPassword", newPassword, "confirmPassword", newPassword);
    }

    @Test
    void invalidPasswordChangesLeaveTheOriginalPasswordAndSessionsUsable() throws Exception {
        var owner = browser();
        String name = register(owner);
        login(owner, name, PASSWORD);
        for (Object input : java.util.List.of(change(PASSWORD, "a".repeat(7)), change(PASSWORD, "a".repeat(65)),
                change(PASSWORD, "密".repeat(25)), change(PASSWORD, "😀".repeat(19)), change(PASSWORD, "\uD800".repeat(8)),
                Map.of("oldPassword", PASSWORD, "newPassword", "ValidPassword123!", "confirmPassword", "DifferentPassword!"),
                Map.of("oldPassword", PASSWORD, "newPassword", "ValidPassword123!"),
                Map.of("oldPassword", 123, "newPassword", "ValidPassword123!", "confirmPassword", "ValidPassword123!"),
                Map.of("oldPassword", PASSWORD, "newPassword", "ValidPassword123!", "confirmPassword", "ValidPassword123!", "user_id", 1),
                Map.of())) {
            assertThat(post(owner, "/api/account/password", input).statusCode()).isEqualTo(400);
            assertThat(get(owner, "/api/account/profile").statusCode()).isEqualTo(200);
        }
        login(browser(), name, PASSWORD);
    }

    @Test
    void newPasswordsAcceptTheCharacterAndUtf8ByteBoundaries() throws Exception {
        for (String secret : java.util.List.of("a".repeat(8), "a".repeat(64), "密".repeat(24), "😀".repeat(18))) {
            var owner = browser();
            String name = register(owner);
            login(owner, name, PASSWORD);
            assertThat(post(owner, "/api/account/password", change(PASSWORD, secret)).statusCode()).isEqualTo(200);
            login(browser(), name, secret);
            assertThat(get(owner, "/api/account/profile").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void simultaneousPasswordChangesHaveOnlyOneWinnerAndOnlyItsPasswordWorks() throws Exception {
        var first = browser();
        var second = browser();
        String name = register(first);
        login(first, name, PASSWORD);
        login(second, name, PASSWORD);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return post(first, "/api/account/password", change(PASSWORD, "WinnerPasswordAAA!")); });
            var b = executor.submit(() -> { start.await(); return post(second, "/api/account/password", change(PASSWORD, "WinnerPasswordBBB!")); });
            start.countDown();
            var resultA = a.get();
            var resultB = b.get();
            assertThat(java.util.List.of(resultA.statusCode(), resultB.statusCode())).containsExactlyInAnyOrder(200, 401);
            String winner = resultA.statusCode() == 200 ? "WinnerPasswordAAA!" : "WinnerPasswordBBB!";
            String loser = resultA.statusCode() == 200 ? "WinnerPasswordBBB!" : "WinnerPasswordAAA!";
            login(browser(), name, winner);
            assertThat(post(browser(), "/api/auth/login", Map.of("username", name, "password", loser)).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void nicknameLengthAppliesToWhitespaceAndAnEmptyNicknameFallsBackToUsername() throws Exception {
        var owner = browser();
        String name = register(owner);
        login(owner, name, PASSWORD);
        assertThat(post(owner, "/api/account/profile", Map.of("nickname", " ".repeat(31))).statusCode()).isEqualTo(400);
        assertThat(post(owner, "/api/account/profile", Map.of("nickname", "")).statusCode()).isEqualTo(200);
        assertThat(data(get(owner, "/api/account/profile")).path("nickname").asText()).isEqualTo(name);
    }

    String register(HttpClient browser) throws Exception {
        String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        assertThat(post(browser, "/api/auth/register", Map.of("username", name, "nickname", "测试用户",
                "password", PASSWORD, "confirmPassword", PASSWORD)).statusCode()).isEqualTo(201);
        return name;
    }

    void login(HttpClient browser, String name, String password) throws Exception {
        assertThat(post(browser, "/api/auth/login", Map.of("username", name, "password", password)).statusCode()).isEqualTo(200);
    }

    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }

    HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(HttpClient browser, String path, Object body) throws Exception {
        var csrf = data(get(browser, "/api/auth/csrf"));
        return send(browser, path, body, csrf.path("token").asText());
    }

    HttpResponse<String> send(HttpClient browser, String path, Object body, String csrf) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        return browser.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
