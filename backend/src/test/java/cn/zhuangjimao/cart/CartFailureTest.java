package cn.zhuangjimao.cart;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Fault injection affects only this context's transaction boundary; authentication uses real MySQL. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CartFailureTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @MockitoBean(name = "transactionManager") PlatformTransactionManager transactions;
    final ObjectMapper json = new ObjectMapper();

    @Test void transactionStartupFailureReturnsTheCartUnavailableContract() throws Exception {
        String username = "t13fail" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String password = "CartFailure123!";
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'USER')",
                username, passwords.encode(password), username);
        try {
            var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
            var login = post(client, "/api/auth/login", Map.of("username", username, "password", password));
            assertThat(login.statusCode()).isEqualTo(200);
            when(transactions.getTransaction(any())).thenThrow(new CannotCreateTransactionException("Injected transaction failure"));

            assertUnavailable(get(client, "/api/cart"));
            assertUnavailable(post(client, "/api/cart/items", Map.of(
                    "productId", 1, "quantity", 1, "requestKey", "failure-transaction")));
        } finally {
            db.update("DELETE FROM account WHERE username=?", username);
        }
    }

    private void assertUnavailable(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(503);
        var body = json.readTree(response.body());
        assertThat(body.path("code").asText()).isEqualTo("CART_UNAVAILABLE");
        assertThat(body.has("data")).isFalse();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain("Injected transaction failure", "CannotCreateTransactionException", "jdbc:mysql");
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, Object input) throws Exception {
        var csrf = json.readTree(get(client, "/api/auth/csrf").body()).path("data");
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
