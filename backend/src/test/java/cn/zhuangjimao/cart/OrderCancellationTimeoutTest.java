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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Public HTTP assertions; SQL only arranges isolated fixtures, faults and cleanup. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OrderCancellationTimeoutTest.TimeConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderCancellationTimeoutTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    @Autowired OrderTimeouts timeouts;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "o19" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary TestClock paymentClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-16T00:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    }
    @BeforeEach void resetTime() { clock.now.set(Instant.parse("2026-11-16T00:00:00Z")); }

    @Test void ownerCancellationReleasesStockOnceAndKeepsTheCartEmpty() throws Exception {
        var owner = account("happy", "USER");
        var admin = account("stock", "ADMIN");
        long sku = product("happy");
        var pending = create(owner, sku);
        String path = "/api/orders/" + pending.path("orderNumber").asText();
        clock.now.set(Instant.parse("2026-11-16T00:10:00.123456Z"));
        var response = post(owner, path + "/cancel", Map.of());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var cancelled = data(response);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancellationReason").asText()).isEqualTo("USER");
        assertThat(cancelled.path("cancelledAt").asText()).isEqualTo("2026-11-16T00:10:00.123456Z");
        assertThat(cancelled.path("paidAt").isNull()).isTrue();
        assertThat(cancelled.path("items")).isEqualTo(pending.path("items"));
        clock.now.set(Instant.parse("2026-11-16T01:00:00Z"));
        assertThat(data(post(owner, path + "/cancel", Map.of()))).isEqualTo(cancelled);
        assertThat(data(get(owner, path))).isEqualTo(cancelled);
        var balance = data(get(admin, "/api/admin/products/" + sku));
        assertThat(balance.path("product").path("stock").asInt()).isEqualTo(99);
        assertThat(balance.path("reservedStock").asInt()).isZero();
        assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
        assertThat(code(post(owner, path + "/pay", Map.of()))).isEqualTo("ORDER_NOT_PAYABLE");
        var summary = data(get(owner, "/api/orders")).path("items").get(0);
        assertThat(summary.path("cancellationReason").asText()).isEqualTo("USER");
        assertThat(summary.path("cancelledAt")).isEqualTo(cancelled.path("cancelledAt"));
    }

    @Test void delayedScansCancelAtTheDeadlineAndDoNotReleasePaidOrFutureOrders() throws Exception {
        var owner = account("scan", "USER");
        var admin = account("scanStock", "ADMIN");
        long sku = product("scan");
        var due = create(owner, sku);
        String path = "/api/orders/" + due.path("orderNumber").asText();
        var paid = create(owner, sku);
        assertThat(post(owner, "/api/orders/" + paid.path("orderNumber").asText() + "/pay", Map.of()).statusCode()).isEqualTo(200);
        clock.now.set(Instant.parse("2026-11-16T00:30:00Z").minusNanos(1));
        timeouts.scan();
        assertThat(data(get(owner, path))).isEqualTo(due);
        clock.now.set(Instant.parse("2026-11-16T00:30:00Z"));
        assertThat(code(post(owner, path + "/pay", Map.of()))).isEqualTo("ORDER_PAYMENT_EXPIRED");
        timeouts.scan();
        var cancelled = data(get(owner, path));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancellationReason").asText()).isEqualTo("TIMEOUT");
        assertThat(cancelled.path("cancelledAt").asText()).isEqualTo("2026-11-16T00:30:00Z");
        var late = create(owner, sku);
        clock.now.set(Instant.parse("2026-11-16T01:05:00Z"));
        assertThat(code(post(owner, "/api/orders/" + late.path("orderNumber").asText() + "/pay", Map.of()))).isEqualTo("ORDER_PAYMENT_EXPIRED");
        timeouts.scan();
        assertThat(data(get(owner, "/api/orders/" + late.path("orderNumber").asText())).path("cancellationReason").asText()).isEqualTo("TIMEOUT");
        timeouts.scan();
        assertThat(data(post(owner, path + "/cancel", Map.of()))).isEqualTo(cancelled);
        assertThat(data(get(owner, "/api/orders/" + paid.path("orderNumber").asText())).path("status").asText()).isEqualTo("PAID");
        var balance = data(get(admin, "/api/admin/products/" + sku));
        assertThat(balance.path("product").path("stock").asInt()).isEqualTo(96);
        assertThat(balance.path("reservedStock").asInt()).isEqualTo(3);
        assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
    }

    @Test void cancellationRejectsForeignOwnersCsrfForgedFieldsAndPaidOrCompletedStates() throws Exception {
        var owner = account("access", "USER");
        var other = account("other", "USER");
        long sku = product("access");
        var pending = create(owner, sku);
        String path = "/api/orders/" + pending.path("orderNumber").asText();
        assertThat(post(HttpClient.newBuilder().build(), path + "/cancel", Map.of()).statusCode()).isEqualTo(401);
        assertThat(post(other, path + "/cancel", Map.of()).statusCode()).isEqualTo(404);
        assertThat(post(owner, "/api/orders/missing/cancel", Map.of()).statusCode()).isEqualTo(404);
        assertThat(raw(owner, path + "/cancel", "{}", "invalid").statusCode()).isEqualTo(403);
        String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
        for (String body : List.of("[]", "null", "{", "{} {}", "{\"reason\":\"TIMEOUT\"}",
                "{\"status\":\"CANCELLED\"}", "{\"accountId\":1}", "{\"cancelledAt\":0}")) {
            var response = raw(owner, path + "/cancel", body, token);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(code(response)).isEqualTo("INVALID_CANCELLATION_INPUT");
        }
        assertThat(data(get(owner, path))).isEqualTo(pending);
        assertThat(post(owner, path + "/pay", Map.of()).statusCode()).isEqualTo(200);
        assertThat(code(post(owner, path + "/cancel", Map.of()))).isEqualTo("ORDER_NOT_CANCELLABLE");
        db.update("UPDATE mall_order SET status='COMPLETED' WHERE order_number=?", pending.path("orderNumber").asText());
        assertThat(code(post(owner, path + "/cancel", Map.of()))).isEqualTo("ORDER_NOT_CANCELLABLE");
        assertThat(code(post(owner, path + "/pay", Map.of()))).isEqualTo("ORDER_NOT_PAYABLE");
    }

    @Test void releaseAndFinalStatusFailuresRollBackAllSkusAndRemainRetryable() throws Exception {
        var owner = account("fault", "USER");
        var admin = account("faultStock", "ADMIN");
        long first = product("faultFirst"), second = product("faultSecond");
        var pending = create(owner, List.of(first, second));
        String number = pending.path("orderNumber").asText(), path = "/api/orders/" + number;
        String constraint = "fault19_" + prefix;
        for (boolean release : List.of(true, false)) {
            String table = release ? "products" : "mall_order";
            db.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + (release
                    ? " CHECK(sku<>'" + prefix + "faultSecond' OR stock<99)"
                    : " CHECK(order_number<>'" + number + "' OR status<>'CANCELLED')"));
            try {
                assertThat(post(owner, path + "/cancel", Map.of()).statusCode()).isEqualTo(503);
                assertThat(data(get(owner, path))).isEqualTo(pending);
                for (long sku : List.of(first, second)) {
                    var balance = data(get(admin, "/api/admin/products/" + sku));
                    assertThat(balance.path("product").path("stock").asInt()).isEqualTo(96);
                    assertThat(balance.path("reservedStock").asInt()).isEqualTo(3);
                }
            } finally { db.execute("ALTER TABLE " + table + " DROP CHECK " + constraint); }
        }
        assertThat(data(post(owner, path + "/cancel", Map.of())).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(post(owner, path + "/cancel", Map.of()).statusCode()).isEqualTo(200);
        for (long sku : List.of(first, second)) {
            var balance = data(get(admin, "/api/admin/products/" + sku));
            assertThat(balance.path("product").path("stock").asInt()).isEqualTo(99);
            assertThat(balance.path("reservedStock").asInt()).isZero();
        }
    }

    @Test void scanContinuesPastReleaseFailureAndRetriesItOnTheNextRun() throws Exception {
        var owner = account("retry", "USER");
        var admin = account("retryStock", "ADMIN");
        long bad = product("retryBad"), good = product("retryGood");
        var first = create(owner, bad);
        var second = create(owner, good);
        boolean firstFails = first.path("orderNumber").asText().compareTo(second.path("orderNumber").asText()) < 0;
        var blocked = firstFails ? first : second;
        var healthy = firstFails ? second : first;
        long blockedSku = firstFails ? bad : good;
        String blockedPath = "/api/orders/" + blocked.path("orderNumber").asText();
        String constraint = "scan19_" + prefix;
        db.execute("ALTER TABLE products ADD CONSTRAINT " + constraint + " CHECK(sku<>'" + prefix + (firstFails ? "retryBad" : "retryGood") + "' OR stock<99)");
        clock.now.set(Instant.parse("2026-11-16T01:00:00Z"));
        try {
            timeouts.scan();
            assertThat(data(get(owner, blockedPath))).isEqualTo(blocked);
            assertThat(data(get(owner, "/api/orders/" + healthy.path("orderNumber").asText())).path("status").asText()).isEqualTo("CANCELLED");
            var balance = data(get(admin, "/api/admin/products/" + blockedSku));
            assertThat(balance.path("product").path("stock").asInt()).isEqualTo(96);
            assertThat(balance.path("reservedStock").asInt()).isEqualTo(3);
        } finally { db.execute("ALTER TABLE products DROP CHECK " + constraint); }
        timeouts.scan();
        var cancelled = data(get(owner, blockedPath));
        assertThat(cancelled.path("cancellationReason").asText()).isEqualTo("TIMEOUT");
        assertThat(post(owner, blockedPath + "/cancel", Map.of()).statusCode()).isEqualTo(200);
        timeouts.scan();
        var balance = data(get(admin, "/api/admin/products/" + blockedSku));
        assertThat(balance.path("product").path("stock").asInt()).isEqualTo(99);
        assertThat(balance.path("reservedStock").asInt()).isZero();
    }

    @Test void realConcurrentPaymentsCancellationsAndScansProduceOneLegalResult() throws Exception {
        var owner = account("race", "USER");
        var admin = account("raceStock", "ADMIN");
        for (boolean expired : List.of(false, true)) {
            clock.now.set(Instant.parse("2026-11-16T00:00:00Z"));
            long sku = product("race" + expired);
            var pending = create(owner, sku);
            String path = "/api/orders/" + pending.path("orderNumber").asText();
            clock.now.set(Instant.parse(expired ? "2026-11-16T00:30:00Z" : "2026-11-16T00:29:59.999999999Z"));
            String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
            var pool = Executors.newFixedThreadPool(8);
            var barrier = new CyclicBarrier(8);
            var paidResponses = new AtomicInteger();
            var cancelledResponses = new AtomicInteger();
            try {
                var requests = new ArrayList<Future<?>>();
                for (int index = 0; index < 8; index++) {
                    final int action = index % 3;
                    requests.add(pool.submit(() -> {
                        barrier.await();
                        if (action == 2) { timeouts.scan(); return null; }
                        var response = raw(owner, path + (action == 0 ? "/pay" : "/cancel"), "{}", token);
                        assertThat(response.statusCode()).as(response.body()).isIn(200, 409);
                        if (response.statusCode() == 200) {
                            if (action == 0) paidResponses.incrementAndGet();
                            else cancelledResponses.incrementAndGet();
                        }
                        return null;
                    }));
                }
                for (var request : requests) request.get(20, TimeUnit.SECONDS);
                var result = data(get(owner, path));
                String status = result.path("status").asText();
                if (expired) assertThat(status).isEqualTo("CANCELLED");
                else assertThat(status).isIn("CANCELLED", "PAID");
                // Every successful reply must agree with the one committed outcome.
                assertThat(paidResponses.get()).isEqualTo(status.equals("PAID") ? 3 : 0);
                assertThat(cancelledResponses.get()).isEqualTo(status.equals("CANCELLED") ? 3 : 0);
                var balance = data(get(admin, "/api/admin/products/" + sku));
                assertThat(balance.path("product").path("stock").asInt()).isEqualTo(status.equals("PAID") ? 96 : 99);
                assertThat(balance.path("reservedStock").asInt()).isEqualTo(status.equals("PAID") ? 3 : 0);
                assertThat(result.path(status.equals("PAID") ? "cancelledAt" : "paidAt").isNull()).isTrue();
                if (status.equals("CANCELLED")) {
                    assertThat(data(post(owner, path + "/cancel", Map.of()))).isEqualTo(result);
                    assertThat(code(post(owner, path + "/pay", Map.of()))).isEqualTo("ORDER_NOT_PAYABLE");
                } else assertThat(code(post(owner, path + "/cancel", Map.of()))).isEqualTo("ORDER_NOT_CANCELLABLE");
            } finally { pool.shutdownNow(); }
        }
    }

    @Test void scanTraversesMoreThanOneBatchAndLeavesNewOrdersPending() throws Exception {
        var owner = account("batches", "USER");
        var admin = account("batchStock", "ADMIN");
        long sku = product("batches");
        db.update("UPDATE products SET stock=999 WHERE id=?", sku);
        for (int index = 0; index < 101; index++) create(owner, sku);
        clock.now.set(Instant.parse("2026-11-16T00:30:00Z"));
        var future = create(owner, sku);
        timeouts.scan();
        int cancelled = 0;
        for (int page = 1; page <= 6; page++) {
            for (var order : data(get(owner, "/api/orders?page=" + page)).path("items")) {
                if (order.path("orderNumber").equals(future.path("orderNumber"))) {
                    assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
                } else {
                    assertThat(order.path("status").asText()).isEqualTo("CANCELLED");
                    assertThat(order.path("cancellationReason").asText()).isEqualTo("TIMEOUT");
                    cancelled++;
                }
            }
        }
        assertThat(cancelled).isEqualTo(101);
        timeouts.scan();
        var balance = data(get(admin, "/api/admin/products/" + sku));
        assertThat(balance.path("product").path("stock").asInt()).isEqualTo(996);
        assertThat(balance.path("reservedStock").asInt()).isEqualTo(3);
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
            VALUES(?,'PSU',?,'任务19夹具','Cancellation-19','取消测试','/images/psu.svg',10.01,99,'ON_SALE','https://example.com/psu','隔离测试','test')
            """, prefix + suffix, "取消夹具 " + suffix);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, prefix + suffix);
        db.update("INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) VALUES(?,'取消夹具','[]','test',CURRENT_DATE)", id);
        return id;
    }
    private JsonNode create(HttpClient owner, long product) throws Exception {
        return create(owner, List.of(product));
    }
    private JsonNode create(HttpClient owner, List<Long> products) throws Exception {
        JsonNode cart = null;
        for (long product : products) cart = data(post(owner, "/api/cart/items", Map.of("productId", product, "quantity", 3, "requestKey", UUID.randomUUID().toString())));
        var ids = new ArrayList<Long>();
        cart.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        var selection = Map.of("revision", cart.path("revision").asLong(), "itemIds", ids, "configurationIds", List.of());
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
