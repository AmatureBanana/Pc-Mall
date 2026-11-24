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
@Import(OrderAdministrationTest.TimeConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderAdministrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "o20" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

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

    @Test void administratorQueriesOtherBuyersSnapshotsWithPaginationAndProtectedAccess() throws Exception {
        var owner = account("buyer", "USER");
        var admin = account("admin", "ADMIN");
        long sku = product("query");
        var original = create(owner, sku);
        String number = original.path("orderNumber").asText();
        db.update("UPDATE products SET name='Changed',price=99.99,status='OFF_SALE' WHERE id=?", sku);
        String path = "/api/admin/orders";
        assertThat(get(HttpClient.newHttpClient(), path).statusCode()).isEqualTo(401);
        assertThat(get(owner, path).statusCode()).isEqualTo(403);
        var response = get(admin, path + "?username=" + prefix + "buyer&status=PENDING_PAYMENT&pageSize=10");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var page = data(response);
        assertThat(page.path("total").asInt()).isEqualTo(1);
        var detail = page.path("items").get(0);
        assertThat(detail.path("buyer").path("username").asText()).isEqualTo(prefix + "buyer");
        assertThat(detail.path("order")).isEqualTo(original);
        assertThat(data(get(admin, path + "/" + number))).isEqualTo(detail);
        assertThat(data(get(admin, path + "?orderNumber=" + number)).path("total").asInt()).isEqualTo(1);
        assertThat(data(get(admin, path + "?username=" + prefix + "buyer&page=2&pageSize=10")).path("items")).isEmpty();
        assertThat(data(get(admin, path + "?orderNumber=%25")).path("items")).isEmpty();
        for (String query : List.of("status=FORGED", "page=0", "pageSize=11", "accountId=-1")) {
            assertThat(get(admin, path + "?" + query).statusCode()).as(query).isEqualTo(400);
        }
        assertThat(get(owner, path + "/" + number).statusCode()).isEqualTo(403);
    }

    @Test void onlyPaidOrdersCompleteWithOneAtomicAuditAndNoInventoryChange() throws Exception {
        var owner = account("finish", "USER");
        var admin = account("finishAdm", "ADMIN");
        long sku = product("finish");
        var pending = create(owner, sku);
        String number = pending.path("orderNumber").asText();
        String path = "/api/admin/orders/" + number;
        assertThat(post(owner, path + "/complete", Map.of()).statusCode()).isEqualTo(403);
        assertThat(post(admin, path + "/complete", Map.of()).statusCode()).isEqualTo(409);
        assertThat(data(get(admin, path + "/audit"))).isEmpty();
        assertThat(post(owner, "/api/orders/" + number + "/pay", Map.of()).statusCode()).isEqualTo(200);
        var stock = data(get(admin, "/api/admin/products/" + sku));
        clock.now.set(Instant.parse("2026-11-13T01:00:00.123456Z"));
        var result = post(admin, path + "/complete", Map.of());
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        var completed = data(result);
        assertThat(completed.path("order").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("order").path("completedAt").asText()).isEqualTo("2026-11-13T01:00:00.123456Z");
        assertThat(completed.path("order").path("completedBy").asText()).isEqualTo(prefix + "finishAdm");
        assertThat(completed.path("order").path("items")).isEqualTo(pending.path("items"));
        clock.now.set(Instant.parse("2026-11-14T01:00:00Z"));
        assertThat(data(post(admin, path + "/complete", Map.of()))).isEqualTo(completed);
        assertThat(data(get(admin, "/api/admin/products/" + sku))).isEqualTo(stock);
        var audit = data(get(admin, path + "/audit"));
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).path("actorUsername").asText()).isEqualTo(prefix + "finishAdm");
        assertThat(audit.get(0).path("action").asText()).isEqualTo("COMPLETE");
        assertThat(audit.get(0).path("beforeValue").path("status").asText()).isEqualTo("PAID");
        assertThat(audit.get(0).path("afterValue").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data(get(owner, "/api/orders/" + number))).isEqualTo(completed.path("order"));
    }

    @Test void disablingABuyerPreservesTheirPaidOrder() throws Exception {
        var owner = account("held", "USER");
        var admin = account("holdAdm", "ADMIN");
        var created = create(owner, product("held"));
        String number = created.path("orderNumber").asText();
        String orderPath = "/api/orders/" + number;
        var payment = post(owner, orderPath + "/pay", Map.of());
        assertThat(payment.statusCode()).isEqualTo(200);
        var paid = data(payment);
        long buyerId = data(get(owner, "/api/auth/me")).path("id").asLong();
        String statusPath = "/api/admin/users/" + buyerId + "/status";

        assertThat(post(admin, statusPath, Map.of("status", "DISABLED", "revision", 0)).statusCode()).isEqualTo(200);
        assertThat(get(owner, orderPath).statusCode()).isEqualTo(401);
        assertThat(data(get(admin, "/api/admin/orders/" + number)).path("order")).isEqualTo(paid);

        assertThat(post(admin, statusPath, Map.of("status", "ACTIVE", "revision", 1)).statusCode()).isEqualTo(200);
        assertThat(get(owner, orderPath).statusCode()).isEqualTo(401);
        assertThat(post(owner, "/api/auth/login", Map.of("username", prefix + "held",
                "password", "PaymentPassword123!")).statusCode()).isEqualTo(200);
        assertThat(data(get(owner, orderPath))).isEqualTo(paid);
    }

    @Test void concurrentAdministratorsProduceOneCompletionAndAudit() throws Exception {
        var owner = account("race", "USER");
        var admins = List.of(account("raceA", "ADMIN"), account("raceB", "ADMIN"));
        long sku = product("race");
        String number = create(owner, sku).path("orderNumber").asText();
        String path = "/api/admin/orders/" + number;
        assertThat(post(owner, "/api/orders/" + number + "/pay", Map.of()).statusCode()).isEqualTo(200);
        var stock = data(get(admins.get(0), "/api/admin/products/" + sku));
        var tokens = new ArrayList<String>();
        for (var admin : admins) tokens.add(data(get(admin, "/api/auth/csrf")).path("token").asText());
        var executor = Executors.newFixedThreadPool(6);
        var barrier = new CyclicBarrier(6);
        try {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < 6; i++) {
                int actor = i % 2;
                futures.add(executor.submit(() -> { barrier.await(); return raw(admins.get(actor), path + "/complete", "{}", tokens.get(actor)); }));
            }
            JsonNode completed = null;
            for (var future : futures) {
                var response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                if (completed == null) completed = data(response);
                else assertThat(data(response)).isEqualTo(completed);
            }
            assertThat(completed.path("order").path("status").asText()).isEqualTo("COMPLETED");
            var audit = data(get(admins.get(0), path + "/audit"));
            assertThat(audit).hasSize(1);
            assertThat(audit.get(0).path("actorUsername")).isEqualTo(completed.path("order").path("completedBy"));
            assertThat(data(get(admins.get(0), "/api/admin/products/" + sku))).isEqualTo(stock);
        } finally { executor.shutdownNow(); }
    }

    @Test void auditFailureRollsBackCompletionAndForgedOrIllegalTransitionsAreRejected() throws Exception {
        var owner = account("fault", "USER");
        var admin = account("faultA", "ADMIN");
        long sku = product("fault");
        String number = create(owner, sku).path("orderNumber").asText();
        String path = "/api/admin/orders/" + number;
        assertThat(post(owner, "/api/orders/" + number + "/pay", Map.of()).statusCode()).isEqualTo(200);
        var before = data(get(admin, path));
        var stock = data(get(admin, "/api/admin/products/" + sku));
        assertThat(post(owner, "/api/orders/" + number + "/cancel", Map.of()).statusCode()).isEqualTo(409);
        for (String body : List.of("{\"status\":\"COMPLETED\"}", "{\"total\":0}", "{\"items\":[]}", "{\"actor\":\"forged\"}", "{} {}", "[]", "{", "{\"completedAt\":null}")) {
            String token = data(get(admin, "/api/auth/csrf")).path("token").asText();
            assertThat(code(raw(admin, path + "/complete", body, token))).as(body).isEqualTo("INVALID_COMPLETION_INPUT");
        }
        assertThat(raw(admin, path + "/complete", "{}", "wrong").statusCode()).isEqualTo(403);
        assertThat(post(admin, path, Map.of("status", "CANCELLED")).statusCode()).isNotEqualTo(200);
        String constraint = "fault20_" + prefix;
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT " + constraint + " CHECK(object_type<>'ORDER' OR actor_username<>'" + prefix + "faultA')");
        try {
            assertThat(post(admin, path + "/complete", Map.of()).statusCode()).isEqualTo(503);
            assertThat(data(get(admin, path))).isEqualTo(before);
            assertThat(data(get(admin, path + "/audit"))).isEmpty();
            assertThat(data(get(admin, "/api/admin/products/" + sku))).isEqualTo(stock);
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK " + constraint); }
        assertThat(post(admin, path + "/complete", Map.of()).statusCode()).isEqualTo(200);
        assertThat(data(get(admin, path + "/audit"))).hasSize(1);
        String cancelled = create(owner, sku).path("orderNumber").asText();
        assertThat(post(owner, "/api/orders/" + cancelled + "/cancel", Map.of()).statusCode()).isEqualTo(200);
        assertThat(post(admin, "/api/admin/orders/" + cancelled + "/complete", Map.of()).statusCode()).isEqualTo(409);
        assertThat(post(owner, "/api/orders/" + cancelled + "/pay", Map.of()).statusCode()).isEqualTo(409);
        assertThat(get(owner, path + "/audit").statusCode()).isEqualTo(403);
        assertThat(post(admin, "/api/admin/orders/missing/complete", Map.of()).statusCode()).isEqualTo(404);
    }

    @Test void mixedGroupSnapshotsAndEveryStatusRemainQueryableAcrossBuyers() throws Exception {
        var owner = account("mixed", "USER");
        var other = account("mixedOther", "USER");
        var admin = account("mixedAdm", "ADMIN");
        var parts = configuration("mixed");
        long cpu = (long) parts.get(0).get("productId");
        var grouped = post(owner, "/api/cart/configurations", Map.of("selections", parts, "memoryQuantity", 2));
        assertThat(grouped.statusCode()).as(grouped.body()).isEqualTo(200);
        var cart = data(post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 3, "requestKey", UUID.randomUUID().toString())));
        var input = new LinkedHashMap<String,Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", List.of(cart.path("items").get(0).path("id").asLong()));
        input.put("configurationIds", List.of(cart.path("configurations").get(0).path("id").asLong()));
        var quote = post(owner, "/api/cart/checkout-preview", input);
        assertThat(quote.statusCode()).as(quote.body()).isEqualTo(200);
        input.put("quoteToken", data(quote).path("quoteToken").asText());
        input.put("requestKey", UUID.randomUUID().toString());
        var created = post(owner, "/api/orders", input);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        var original = data(created);
        String number = original.path("orderNumber").asText();
        db.update("UPDATE products SET name='Changed',price=99.99,status='OFF_SALE' WHERE sku LIKE ?", prefix + "mixed%");
        var detail = data(get(admin, "/api/admin/orders/" + number));
        assertThat(detail.path("order")).isEqualTo(original);
        assertThat(detail.path("order").path("configurations")).hasSize(1);
        assertThat(detail.path("order").path("items")).hasSize(9);
        assertThat(post(owner, "/api/orders/" + number + "/pay", Map.of()).statusCode()).isEqualTo(200);
        assertThat(post(admin, "/api/admin/orders/" + number + "/complete", Map.of()).statusCode()).isEqualTo(200);
        var completed = data(get(admin, "/api/admin/orders/" + number)).path("order");
        assertThat(completed.path("items")).isEqualTo(original.path("items"));
        assertThat(completed.path("configurations")).isEqualTo(original.path("configurations"));
        long sku = product("states");
        var pending = create(owner, sku);
        String paid = create(owner, sku).path("orderNumber").asText();
        assertThat(post(owner, "/api/orders/" + paid + "/pay", Map.of()).statusCode()).isEqualTo(200);
        String cancelled = create(owner, sku).path("orderNumber").asText();
        assertThat(post(owner, "/api/orders/" + cancelled + "/cancel", Map.of()).statusCode()).isEqualTo(200);
        create(other, sku);
        long accountId = data(get(owner, "/api/auth/me")).path("id").asLong();
        for (var entry : Map.of("COMPLETED", number, "PAID", paid, "CANCELLED", cancelled, "PENDING_PAYMENT", pending.path("orderNumber").asText()).entrySet()) {
            var page = data(get(admin, "/api/admin/orders?accountId=" + accountId + "&status=" + entry.getKey()));
            assertThat(page.path("total").asInt()).isEqualTo(1);
            assertThat(page.path("items").get(0).path("order").path("orderNumber").asText()).isEqualTo(entry.getValue());
        }
        for (int i = 0; i < 8; i++) create(owner, sku);
        var first = data(get(admin, "/api/admin/orders?accountId=" + accountId + "&pageSize=10"));
        var second = data(get(admin, "/api/admin/orders?accountId=" + accountId + "&pageSize=10&page=2"));
        assertThat(first.path("total").asInt()).isEqualTo(12);
        assertThat(first.path("items")).hasSize(10);
        assertThat(second.path("items")).hasSize(2);
    }

    private List<Map<String, Object>> configuration(String suffix) {
        long memory = db.queryForObject("SELECT id FROM products WHERE sku='KINGSTON-KF560C36BBE2-16'", Long.class);
        var templates = List.of(1L, 3L, 5L, memory, 9L, 11L, 12L, 8L);
        var parts = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < templates.size(); index++) {
            String sku = prefix + suffix + index;
            long template = templates.get(index);
            db.update("""
                INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
                SELECT ?,category,name,brand,model,summary,image_url,price,200,'ON_SALE',source_url,price_note,data_version FROM products WHERE id=?
                """, sku, template);
            long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
            db.update("INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) SELECT ?,description,parameters,data_version,verified_on FROM product_specifications WHERE product_id=?", id, template);
            String category = db.queryForObject("SELECT category FROM products WHERE id=?", String.class, id);
            parts.add(Map.of("category", category, "productId", id));
        }
        return parts;
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
        db.update("DELETE FROM admin_audit WHERE actor_username LIKE ?", prefix + "%");
        var references = db.queryForList("SELECT reservation_reference FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", String.class, prefix + "%");
        db.update("DELETE r FROM order_request r JOIN account a ON a.id=r.account_id WHERE a.username LIKE ?", prefix + "%");
        db.update("DELETE i FROM order_item i JOIN mall_order o ON o.order_number=i.order_number JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
        db.update("DELETE o FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
        references.forEach(reference -> {
            db.update("DELETE FROM stock_reservation_items WHERE reference=?", reference);
            db.update("DELETE FROM stock_reservations WHERE reference=?", reference);
        });
        for (long id : db.queryForList("SELECT id FROM account WHERE username LIKE ?", Long.class, prefix + "%")) {
            db.update("DELETE FROM cart_request WHERE account_id=?", id);
            db.update("DELETE g FROM cart_configuration g JOIN shopping_cart c ON c.id=g.cart_id WHERE c.account_id=?", id);
            db.update("DELETE i FROM shopping_cart_item i JOIN shopping_cart c ON c.id=i.cart_id WHERE c.account_id=?", id);
            db.update("DELETE FROM shopping_cart WHERE account_id=?", id);
        }
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }
}
