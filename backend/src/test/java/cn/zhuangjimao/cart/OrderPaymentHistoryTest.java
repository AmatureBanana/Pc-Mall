package cn.zhuangjimao.cart;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Public HTTP assertions; SQL only arranges isolated fixtures, faults and cleanup. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OrderPaymentHistoryTest.TimeConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderPaymentHistoryTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "o18" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary TestClock paymentClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-13T00:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    }
    @BeforeEach void resetTime() { clock.now.set(Instant.parse("2026-11-13T00:00:00Z")); }

    @Test void ownerPaysTheSnapshotAmountOnceWithoutChangingAvailableOrReservedStock() throws Exception {
        var owner = account("happy", "USER");
        var admin = account("stock", "ADMIN");
        long sku = product("happy");
        var pending = create(owner, sku);
        String path = "/api/orders/" + pending.path("orderNumber").asText();
        var balance = data(get(admin, "/api/admin/products/" + sku));
        db.update("UPDATE products SET price=99.99,status='OFF_SALE',revision=revision+1 WHERE id=?", sku);
        clock.now.set(Instant.parse("2026-11-13T00:10:00.123456Z"));
        var response = post(owner, path + "/pay", Map.of());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var paid = data(response);
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("paidAt").asText()).isEqualTo("2026-11-13T00:10:00.123456Z");
        assertThat(paid.path("total").asText()).isEqualTo("30.03");
        assertThat(paid.path("items")).isEqualTo(pending.path("items"));
        clock.now.set(Instant.parse("2026-11-13T01:00:00Z"));
        assertThat(data(post(owner, path + "/pay", Map.of()))).isEqualTo(paid);
        assertThat(data(get(owner, path))).isEqualTo(paid);
        var after = data(get(admin, "/api/admin/products/" + sku));
        assertThat(after.path("product").path("stock")).isEqualTo(balance.path("product").path("stock"));
        assertThat(after.path("reservedStock")).isEqualTo(balance.path("reservedStock"));
    }

    @Test void historyIsPaginatedNewestFirstAndOnlyShowsTheSessionOwnersOrders() throws Exception {
        var owner = account("history", "USER");
        var other = account("foreign", "USER");
        assertThat(data(get(other, "/api/orders")).path("items")).isEmpty();
        long sku = product("history");
        var first = create(owner, sku);
        String firstNumber = first.path("orderNumber").asText();
        String latestNumber = firstNumber;
        String previousNumber = firstNumber;
        for (int index = 1; index <= 20; index++) {
            clock.now.set(Instant.parse("2026-11-13T00:00:00Z").plusSeconds(index));
            previousNumber = latestNumber;
            latestNumber = create(owner, sku).path("orderNumber").asText();
        }
        long account = data(get(owner, "/api/auth/me")).path("id").asLong();
        db.update("UPDATE products SET name='后来的名字',price=88.88,status='OFF_SALE',revision=revision+1 WHERE id=?", sku);
        var response = get(owner, "/api/orders");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var page = data(response);
        assertThat(page.path("total").asInt()).isEqualTo(21);
        assertThat(page.path("items")).hasSize(20);
        var numbers = new ArrayList<String>();
        page.path("items").forEach(item -> numbers.add(item.path("orderNumber").asText()));
        assertThat(numbers.subList(0, 2)).containsExactly(latestNumber, previousNumber);
        assertThat(page.path("items").get(0).path("total").asText()).isEqualTo("30.03");
        assertThat(data(get(owner, "/api/orders?page=2")).path("items")).hasSize(1);
        assertThat(data(get(owner, "/api/orders?page=3")).path("items")).isEmpty();
        assertThat(data(get(other, "/api/orders?accountId=" + account)).path("total").asInt()).isZero();
        assertThat(get(other, "/api/orders/" + firstNumber).statusCode()).isEqualTo(404);
        assertThat(data(get(owner, "/api/orders/" + firstNumber))).isEqualTo(first);
        assertThat(get(owner, "/api/orders?page=0").statusCode()).isEqualTo(400);
        assertThat(get(owner, "/api/orders?page=invalid").statusCode()).isEqualTo(400);
    }

    @Test void paymentRequiresTheOwnerSessionCsrfAndNoClientSuppliedPaymentFields() throws Exception {
        var owner = account("secure", "USER");
        var other = account("intruder", "USER");
        var order = create(owner, product("secure"));
        String path = "/api/orders/" + order.path("orderNumber").asText();
        assertThat(post(HttpClient.newBuilder().cookieHandler(new CookieManager()).build(), path + "/pay", Map.of()).statusCode()).isEqualTo(401);
        assertThat(get(HttpClient.newBuilder().build(), "/api/orders").statusCode()).isEqualTo(401);
        assertThat(post(other, path + "/pay", Map.of()).statusCode()).isEqualTo(404);
        assertThat(raw(owner, path + "/pay", "{}", "invalid").statusCode()).isEqualTo(403);
        for (var field : List.of("accountId", "total", "paidAt", "status")) {
            assertThat(code(post(owner, path + "/pay", Map.of(field, "forged")))).isEqualTo("INVALID_PAYMENT_INPUT");
        }
        assertThat(data(get(owner, path))).isEqualTo(order);
        var paid = post(owner, path + "/pay", Map.of());
        assertThat(paid.statusCode()).as(paid.body()).isEqualTo(200);
    }

    @Test void deadlineComparisonRejectsEqualityAndExpiryEvenBeforeAnyScannerRuns() throws Exception {
        var owner = account("deadline", "USER");
        long sku = product("deadline");
        for (long offset : List.of(-1L, 0L, 1L)) {
            clock.now.set(Instant.parse("2026-11-13T00:00:00Z"));
            var pending = create(owner, sku);
            String path = "/api/orders/" + pending.path("orderNumber").asText();
            clock.now.set(Instant.parse("2026-11-13T00:30:00Z").plusNanos(offset));
            var response = post(owner, path + "/pay", Map.of());
            if (offset < 0) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(data(response).path("paidAt").asText()).isEqualTo("2026-11-13T00:29:59.999999Z");
            } else {
                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(code(response)).isEqualTo("ORDER_PAYMENT_EXPIRED");
                assertThat(data(get(owner, path))).isEqualTo(pending);
            }
        }
    }

    @Test void simultaneousPaymentsReturnOneStableResultWithoutInventorySideEffects() throws Exception {
        var owner = account("race", "USER");
        var admin = account("raceStock", "ADMIN");
        long sku = product("race");
        var pending = create(owner, sku);
        String path = "/api/orders/" + pending.path("orderNumber").asText();
        var balance = data(get(admin, "/api/admin/products/" + sku));
        String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
        var executor = Executors.newFixedThreadPool(6);
        var barrier = new CyclicBarrier(6);
        try {
            var requests = new ArrayList<Future<HttpResponse<String>>>();
            for (int index = 0; index < 6; index++) requests.add(executor.submit(() -> {
                barrier.await();
                return raw(owner, path + "/pay", "{}", token);
            }));
            JsonNode paid = null;
            for (var request : requests) {
                var response = request.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(data(response).path("status").asText()).isEqualTo("PAID");
                if (paid == null) paid = data(response);
                else assertThat(data(response)).isEqualTo(paid);
            }
            assertThat(data(get(owner, path))).isEqualTo(paid);
            assertThat(data(get(admin, "/api/admin/products/" + sku))).isEqualTo(balance);
        } finally { executor.shutdownNow(); }
    }

    @Test void illegalStatesAreRefusedAndPaymentStorageFailureLeavesTheOriginalOrderRetryable() throws Exception {
        var owner = account("fault", "USER");
        long sku = product("fault");
        for (String status : List.of("CANCELLED", "COMPLETED")) {
            var order = create(owner, sku);
            String number = order.path("orderNumber").asText();
            db.update("UPDATE mall_order SET status=? WHERE order_number=?", status, number);
            var response = post(owner, "/api/orders/" + number + "/pay", Map.of());
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(code(response)).isEqualTo("ORDER_NOT_PAYABLE");
            assertThat(data(get(owner, "/api/orders/" + number)).path("status").asText()).isEqualTo(status);
        }
        var pending = create(owner, sku);
        String number = pending.path("orderNumber").asText();
        String constraint = "fault18_" + prefix;
        db.execute("ALTER TABLE mall_order ADD CONSTRAINT " + constraint + " CHECK(order_number<>'" + number + "' OR status<>'PAID')");
        try {
            assertThat(post(owner, "/api/orders/" + number + "/pay", Map.of()).statusCode()).isEqualTo(503);
            assertThat(data(get(owner, "/api/orders/" + number))).isEqualTo(pending);
        } finally { db.execute("ALTER TABLE mall_order DROP CHECK " + constraint); }
        assertThat(data(post(owner, "/api/orders/" + number + "/pay", Map.of())).path("status").asText()).isEqualTo("PAID");
    }

    @Test void paymentWaitingOnAnOrderLockUsesTimeAndStatusAfterTheWait() throws Exception {
        var owner = account("wait", "USER");
        long sku = product("wait");
        for (boolean cancel : List.of(false, true)) {
            clock.now.set(Instant.parse("2026-11-13T00:00:00Z"));
            var pending = create(owner, sku);
            String number = pending.path("orderNumber").asText();
            String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
            var executor = Executors.newSingleThreadExecutor();
            try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
                connection.setAutoCommit(false);
                try (var lock = connection.prepareStatement("SELECT order_number FROM mall_order WHERE order_number=? FOR UPDATE")) {
                    lock.setString(1, number);
                    try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                clock.now.set(Instant.parse("2026-11-13T00:29:59Z"));
                var request = executor.submit(() -> raw(owner, "/api/orders/" + number + "/pay", "{}", token));
                // A held DB lock keeps the HTTP action pending while time / committed status changes.
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> request.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                if (cancel) {
                    try (var update = connection.prepareStatement("UPDATE mall_order SET status='CANCELLED' WHERE order_number=?")) {
                        update.setString(1, number);
                        update.executeUpdate();
                    }
                } else clock.now.set(Instant.parse("2026-11-13T00:30:00Z"));
                connection.commit();
                var refused = request.get(10, TimeUnit.SECONDS);
                assertThat(refused.statusCode()).isEqualTo(409);
                assertThat(code(refused)).isEqualTo(cancel ? "ORDER_NOT_PAYABLE" : "ORDER_PAYMENT_EXPIRED");
                assertThat(data(get(owner, "/api/orders/" + number)).path("paidAt").isNull()).isTrue();
            } finally { executor.shutdownNow(); }
        }
    }

    private HttpClient account(String suffix, String role) throws Exception {
        String name = prefix + suffix;
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES(?,?,?,?)", name, passwords.encode("PaymentPassword123!"), name, role);
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertThat(post(client, "/api/auth/login", Map.of("username", name, "password", "PaymentPassword123!")).statusCode()).isEqualTo(200);
        return client;
    }
    private long product(String suffix) {
        db.update("""
            INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
            VALUES(?,'PSU',?,'任务18夹具','Payment-18','支付测试','/images/psu.svg',10.01,99,'ON_SALE','https://example.com/psu','隔离测试','test')
            """, prefix + suffix, "支付夹具 " + suffix);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, prefix + suffix);
        db.update("INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) VALUES(?,'支付夹具','[]','test',CURRENT_DATE)", id);
        return id;
    }
    private JsonNode create(HttpClient owner, long product) throws Exception {
        var cart = data(post(owner, "/api/cart/items", Map.of("productId", product, "quantity", 3, "requestKey", UUID.randomUUID().toString())));
        var selection = Map.of("revision", cart.path("revision").asLong(), "itemIds", List.of(cart.path("items").get(0).path("id").asLong()), "configurationIds", List.of());
        var quote = data(post(owner, "/api/cart/checkout-preview", selection));
        var input = new LinkedHashMap<String,Object>(selection);
        input.put("quoteToken", quote.path("quoteToken").asText());
        input.put("requestKey", UUID.randomUUID().toString());
        var response = post(owner, "/api/orders", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return data(response);
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, Object input) throws Exception {
        String token = data(get(client, "/api/auth/csrf")).path("token").asText();
        return raw(client, path, json.writeValueAsString(input), token);
    }
    private HttpResponse<String> raw(HttpClient client, String path, String body, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    private String code(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("code").asText(); }

    @AfterAll void cleanup() {
        var references = db.queryForList("SELECT reservation_reference FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", String.class, prefix + "%");
        db.update("DELETE r FROM order_request r JOIN account a ON a.id=r.account_id WHERE a.username LIKE ?", prefix + "%");
        db.update("DELETE o FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
        references.forEach(reference -> {
            db.update("DELETE FROM stock_reservation_items WHERE reference=?", reference);
            db.update("DELETE FROM stock_reservations WHERE reference=?", reference);
        });
        for (long id : db.queryForList("SELECT id FROM account WHERE username LIKE ?", Long.class, prefix + "%")) {
            db.update("DELETE FROM cart_request WHERE account_id=?", id);
            db.update("DELETE i FROM shopping_cart_item i JOIN shopping_cart c ON c.id=i.cart_id WHERE c.account_id=?", id);
            db.update("DELETE FROM shopping_cart WHERE account_id=?", id);
        }
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }
}
