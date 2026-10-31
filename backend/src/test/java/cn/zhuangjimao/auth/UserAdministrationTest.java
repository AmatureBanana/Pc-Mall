package cn.zhuangjimao.auth;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.*;
import java.net.http.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP behavior against the guarded real MySQL schema; SQL only arranges and cleans fixtures/faults. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mall.login-limit.max-failures=100")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserAdministrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t06_" + UUID.randomUUID().toString().substring(0, 8);
    static final String PASSWORD = "UserAdminPassword123!";
    HttpClient admin;

    @BeforeAll void setup() throws Exception {
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'ADMIN')",
                prefix, passwords.encode(PASSWORD), "用户管理员");
        admin = browser();
        login(admin, prefix);
    }

    @AfterAll void cleanup() {
        db.update("DELETE FROM admin_audit WHERE actor_username=?", prefix);
        db.update("DELETE FROM account WHERE LEFT(username,?)=?", prefix.length(), prefix);
    }

    @Test void administratorQueriesOnlyOrdinaryUsersWithPaginationAndSafeDetails() throws Exception {
        var alpha = register("qalpha");
        var bravo = register("qbravo");
        var response = get(admin, "/api/admin/users?username=" + prefix.toUpperCase() + "Q&page=2&size=1");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var page = data(response);
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("page").asInt()).isEqualTo(2);
        assertThat(page.path("size").asInt()).isEqualTo(1);
        assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id")).isEqualTo(alpha.path("id"));
        var detail = data(get(admin, "/api/admin/users/" + bravo.path("id").asLong()));
        assertThat(detail.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "username", "nickname", "role", "status", "revision", "createdAt", "updatedAt");
        assertThat(detail.path("username").asText()).isEqualTo(prefix + "qbravo");
        assertThat(detail.path("role").asText()).isEqualTo("USER");
        assertThat(detail.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(detail.path("createdAt").asText()).isNotBlank();
        assertThat(response.body()).doesNotContain("password", "credential", "JSESSIONID");
        assertThat(data(get(admin, "/api/admin/users?username=" + prefix + "q&status=DISABLED")).path("total").asInt()).isZero();
        assertThat(data(get(admin, "/api/admin/users?username=%25")).path("total").asInt()).isZero();
        for (String query : List.of("page=0", "size=101", "page=invalid", "status=DELETED", "username=" + "a".repeat(21))) {
            assertThat(get(admin, "/api/admin/users?" + query).statusCode()).as(query).isEqualTo(400);
        }
        assertThat(get(admin, "/api/admin/users/0").statusCode()).isEqualTo(400);
        assertThat(get(admin, "/api/admin/users/not-an-id").statusCode()).isEqualTo(400);
        assertThat(get(admin, "/api/admin/users/9223372036854775807").statusCode()).isEqualTo(404);
    }

    @Test void disablingAndEnablingIsAuditedAndRevokesAllOldSessionsWithoutLosingAccountData() throws Exception {
        var original = register("cycle");
        long id = original.path("id").asLong();
        String path = "/api/admin/users/" + id;
        var active = browser();
        var dormant = browser();
        login(active, prefix + "cycle");
        login(dormant, prefix + "cycle");
        String staleToken = data(get(active, "/api/auth/csrf")).path("token").asText();
        var disabled = post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0));
        assertThat(disabled.statusCode()).as(disabled.body()).isEqualTo(200);
        assertThat(data(disabled).path("status").asText()).isEqualTo("DISABLED");
        assertThat(data(disabled).path("revision").asInt()).isEqualTo(1);
        assertThat(send(active, "/api/account/profile", Map.of("nickname", "不应保存"), staleToken).statusCode()).isEqualTo(401);
        assertThat(get(active, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(post(browser(), "/api/auth/login", Map.of("username", prefix + "cycle", "password", PASSWORD)).statusCode()).isEqualTo(401);
        assertThat(data(get(admin, "/api/admin/users?username=" + prefix + "cycle&status=DISABLED")).path("total").asInt()).isEqualTo(1);
        var audit = data(get(admin, path + "/audit"));
        assertThat(audit.size()).isEqualTo(1);
        var record = audit.get(0);
        assertThat(record.path("actorUsername").asText()).isEqualTo(prefix);
        assertThat(record.path("actorId").asLong()).isPositive();
        assertThat(record.path("objectType").asText()).isEqualTo("USER");
        assertThat(record.path("objectId").asLong()).isEqualTo(id);
        assertThat(record.path("action").asText()).isEqualTo("STATUS");
        assertThat(record.path("occurredAt").asText()).isNotBlank();
        assertThat(record.path("beforeValue").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(record.path("afterValue").path("status").asText()).isEqualTo("DISABLED");
        assertThat(audit.toString()).doesNotContain("password", "credential", PASSWORD);
        assertThat(post(admin, path + "/status", Map.of("status", "ACTIVE", "revision", 0)).statusCode()).isEqualTo(409);
        assertThat(post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 1)).statusCode()).isEqualTo(200);
        assertThat(data(get(admin, path + "/audit")).size()).isEqualTo(1);
        assertThat(post(admin, path + "/status", Map.of("status", "ACTIVE", "revision", 1)).statusCode()).isEqualTo(200);
        assertThat(data(get(admin, path)).path("revision").asInt()).isEqualTo(2);
        assertThat(data(get(admin, path + "/audit")).size()).isEqualTo(2);
        // This session made no request while disabled; re-enabling must not resurrect it.
        assertThat(get(dormant, "/api/account/profile").statusCode()).isEqualTo(401);
        var fresh = browser();
        login(fresh, prefix + "cycle");
        assertThat(data(get(fresh, "/api/account/profile"))).isEqualTo(original);
        assertThat(get(admin, "/api/admin/overview").statusCode()).isEqualTo(200);
    }

    @Test void authorizationCsrfProtectedFieldsAndAdministratorsCannotBeBypassed() throws Exception {
        var target = register("guard");
        long id = target.path("id").asLong();
        String path = "/api/admin/users/" + id;
        var user = browser();
        login(user, prefix + "guard");
        for (String endpoint : List.of("/api/admin/users", path, path + "/audit")) {
            assertThat(get(browser(), endpoint).statusCode()).isEqualTo(401);
            assertThat(get(user, endpoint).statusCode()).isEqualTo(403);
        }
        assertThat(post(browser(), path + "/status", Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(401);
        assertThat(post(user, path + "/status", Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(403);
        assertThat(send(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0), null).statusCode()).isEqualTo(403);
        for (Object input : List.of(Map.of(), Map.of("status", "DISABLED"), Map.of("revision", 0),
                Map.of("status", "DELETED", "revision", 0), Map.of("status", 1, "revision", 0),
                Map.of("status", "DISABLED", "revision", -1), Map.of("status", "DISABLED", "revision", 0.5),
                Map.of("status", "DISABLED", "revision", "0"), Map.of("status", "DISABLED", "revision", "9223372036854775808"), List.of())) {
            assertThat(post(admin, path + "/status", input).statusCode()).as(input.toString()).isEqualTo(400);
        }
        assertThat(post(admin, path + "/status", json.readTree("{\"status\":\"DISABLED\",\"revision\":9223372036854775808}")).statusCode()).isEqualTo(400);
        for (String field : List.of("id", "user_id", "username", "password", "passwordHash", "nickname", "role", "credentialVersion", "deleted")) {
            assertThat(post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0, field, "tamper")).statusCode()).as(field).isEqualTo(400);
        }
        var self = data(get(admin, "/api/auth/me"));
        String selfPath = "/api/admin/users/" + self.path("id").asLong();
        assertThat(get(admin, selfPath).statusCode()).isEqualTo(404);
        assertThat(post(admin, selfPath + "/status", Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(403);
        assertThat(get(admin, "/api/admin/overview").statusCode()).isEqualTo(200);
        assertThat(data(get(admin, "/api/admin/users?username=" + prefix)).path("items"))
                .allSatisfy(item -> assertThat(item.path("role").asText()).isEqualTo("USER"));
        assertThat(data(get(admin, path)).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(data(get(admin, path)).path("revision").asInt()).isZero();
        assertThat(data(get(admin, path + "/audit")).isEmpty()).isTrue();
        assertThat(data(get(user, "/api/account/profile"))).isEqualTo(target);
        login(browser(), prefix + "guard");
    }

    @Test void auditFailureRollsBackStatusAndSessionRevocationForDisableAndEnable() throws Exception {
        var original = register("atomic");
        long id = original.path("id").asLong();
        String path = "/api/admin/users/" + id;
        var owner = browser();
        login(owner, prefix + "atomic");
        String constraint = "fault_" + prefix;
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT " + constraint + " CHECK (object_type <> 'USER' OR object_id <> " + id + ")");
        try {
            assertThat(post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(503);
            assertThat(data(get(admin, path)).path("status").asText()).isEqualTo("ACTIVE");
            assertThat(data(get(admin, path)).path("revision").asInt()).isZero();
            assertThat(data(get(admin, path + "/audit")).isEmpty()).isTrue();
            assertThat(data(get(owner, "/api/account/profile"))).isEqualTo(original);
            login(browser(), prefix + "atomic");
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK " + constraint); }
        assertThat(post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(200);
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT " + constraint + " CHECK (object_type <> 'USER' OR object_id <> " + id
                + " OR JSON_UNQUOTE(JSON_EXTRACT(after_value,'$.status')) <> 'ACTIVE')");
        try {
            assertThat(post(admin, path + "/status", Map.of("status", "ACTIVE", "revision", 1)).statusCode()).isEqualTo(503);
            assertThat(data(get(admin, path)).path("status").asText()).isEqualTo("DISABLED");
            assertThat(data(get(admin, path)).path("revision").asInt()).isEqualTo(1);
            assertThat(data(get(admin, path + "/audit")).size()).isEqualTo(1);
            assertThat(post(browser(), "/api/auth/login", Map.of("username", prefix + "atomic", "password", PASSWORD)).statusCode()).isEqualTo(401);
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK " + constraint); }
        assertThat(post(admin, path + "/status", Map.of("status", "ACTIVE", "revision", 1)).statusCode()).isEqualTo(200);
        assertThat(get(owner, "/api/account/profile").statusCode()).isEqualTo(401);
        var fresh = browser();
        login(fresh, prefix + "atomic");
        assertThat(data(get(fresh, "/api/account/profile"))).isEqualTo(original);
    }

    @Test void simultaneousStatusChangesRejectTheStaleEditorAndCreateOnlyOneAudit() throws Exception {
        var target = register("race");
        String path = "/api/admin/users/" + target.path("id").asLong();
        var second = browser();
        login(second, prefix);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return post(admin, path + "/status", Map.of("status", "DISABLED", "revision", 0)); });
            var other = executor.submit(() -> { start.await(); return post(second, path + "/status", Map.of("status", "DISABLED", "revision", 0)); });
            start.countDown();
            assertThat(List.of(first.get().statusCode(), other.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(data(get(admin, path)).path("status").asText()).isEqualTo("DISABLED");
        assertThat(data(get(admin, path)).path("revision").asInt()).isEqualTo(1);
        assertThat(data(get(admin, path + "/audit")).size()).isEqualTo(1);
    }

    JsonNode register(String suffix) throws Exception {
        var client = browser();
        String username = prefix + suffix;
        var response = post(client, "/api/auth/register", Map.of("username", username, "nickname", "保留的昵称",
                "password", PASSWORD, "confirmPassword", PASSWORD));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        login(client, username);
        return data(get(client, "/api/account/profile"));
    }

    void login(HttpClient client, String username) throws Exception {
        var response = post(client, "/api/auth/login", Map.of("username", username, "password", PASSWORD));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
    HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }
    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception {
        return send(client, path, body, data(get(client, "/api/auth/csrf")).path("token").asText());
    }
    HttpResponse<String> send(HttpClient client, String path, Object body, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
