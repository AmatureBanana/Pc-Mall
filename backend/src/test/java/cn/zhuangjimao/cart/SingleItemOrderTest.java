package cn.zhuangjimao.cart;

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

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HTTP is the assertion boundary; SQL arranges isolated fixtures and persistence faults. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleItemOrderTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource source;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "o16" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    @AfterAll void cleanup() {
        var references = db.queryForList("SELECT DISTINCT i.reference FROM stock_reservation_items i JOIN products p ON p.id=i.product_id WHERE p.sku LIKE ?", String.class, prefix + "%");
        if (db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='mall_order'", Integer.class) > 0) {
            db.update("DELETE r FROM order_request r JOIN account a ON a.id=r.account_id WHERE a.username LIKE ?", prefix + "%");
            db.update("DELETE o FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
        }
        db.update("DELETE i FROM stock_reservation_items i JOIN products p ON p.id=i.product_id WHERE p.sku LIKE ?", prefix + "%");
        references.forEach(reference -> db.update("DELETE FROM stock_reservations WHERE reference=?", reference));
        var accounts = db.queryForList("SELECT id FROM account WHERE username LIKE ?", Long.class, prefix + "%");
        for (long id : accounts) {
            db.update("DELETE FROM cart_request WHERE account_id=?", id);
            db.update("DELETE g FROM cart_configuration g JOIN shopping_cart c ON c.id=g.cart_id WHERE c.account_id=?", id);
            db.update("DELETE i FROM shopping_cart_item i JOIN shopping_cart c ON c.id=i.cart_id WHERE c.account_id=?", id);
            db.update("DELETE FROM shopping_cart WHERE account_id=?", id);
        }
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM admin_audit WHERE actor_username LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }

    @Test void selectedSinglesBecomeOnePendingOrderWithImmutableSnapshotsAndUnselectedItemsRemain() throws Exception {
        var owner = shopper("happy");
        long first = product("first", 4, "10.01");
        long second = product("second", 5, "20.02");
        add(owner, first, 3);
        var cart = add(owner, second, 1);
        long selected = cart.path("items").get(0).path("id").asLong();
        var input = orderInput(owner, cart, List.of(selected), "happy-order");
        var response = post(owner, "/api/orders", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var order = data(response);
        assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("currency").asText()).isEqualTo("CNY");
        assertThat(order.path("total").asText()).isEqualTo("30.03");
        assertThat(Duration.between(Instant.parse(order.path("createdAt").asText()), Instant.parse(order.path("paymentDeadline").asText())))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(order.path("items")).hasSize(1);
        assertThat(order.path("items").get(0).path("sku").asText()).isEqualTo(prefix + "first");
        assertThat(order.path("items").get(0).path("quantity").asInt()).isEqualTo(3);
        assertThat(order.path("items").get(0).path("configurationId").isNull()).isTrue();
        assertThat(order.path("items").get(0).path("amount").asText()).isEqualTo("30.03");
        var remaining = data(get(owner, "/api/cart"));
        assertThat(remaining.path("items")).hasSize(1);
        assertThat(remaining.path("items").get(0).path("productId").asLong()).isEqualTo(second);
        assertThat(data(get(owner, "/api/products/" + first)).path("product").path("stock").asInt()).isEqualTo(1);
        db.update("UPDATE products SET name='changed',model='changed',price=99.99,status='OFF_SALE',revision=revision+1 WHERE id=?", first);
        assertThat(data(get(owner, "/api/orders/" + order.path("orderNumber").asText()))).isEqualTo(order);
        assertThat(data(post(owner, "/api/orders", input))).isEqualTo(order);
    }

    @Test void simultaneousBuyersNeverOversellAndDuplicateSubmissionsReturnOneOrder() throws Exception {
        long sku = product("race", 1, "10.01");
        var left = shopper("left");
        var right = shopper("right");
        var a = add(left, sku, 1);
        var b = add(right, sku, 1);
        var first = orderInput(left, a, List.of(a.path("items").get(0).path("id").asLong()), "race-left");
        var second = orderInput(right, b, List.of(b.path("items").get(0).path("id").asLong()), "race-right");
        String leftToken = data(get(left, "/api/auth/csrf")).path("token").asText();
        String rightToken = data(get(right, "/api/auth/csrf")).path("token").asText();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var l = executor.submit(() -> { barrier.await(); return raw(left, "/api/orders", json.writeValueAsString(first), leftToken); });
            var r = executor.submit(() -> { barrier.await(); return raw(right, "/api/orders", json.writeValueAsString(second), rightToken); });
            var responses = List.of(l.get(10, TimeUnit.SECONDS), r.get(10, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode)).as(responses.toString()).containsExactlyInAnyOrder(200, 409);
            var winner = responses.get(0).statusCode() == 200 ? left : right;
            var request = winner == left ? first : second;
            String token = winner == left ? leftToken : rightToken;
            var again = new CyclicBarrier(2);
            var one = executor.submit(() -> { again.await(); return raw(winner, "/api/orders", json.writeValueAsString(request), token); });
            var two = executor.submit(() -> { again.await(); return raw(winner, "/api/orders", json.writeValueAsString(request), token); });
            var replay = one.get(10, TimeUnit.SECONDS);
            assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
            assertThat(data(two.get(10, TimeUnit.SECONDS))).isEqualTo(data(replay));
            assertThat(data(get(winner, "/api/products/" + sku)).path("product").path("stock").asInt()).isZero();
            assertThat(data(get(winner == left ? right : left, "/api/cart")).path("items")).hasSize(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders() throws Exception {
        var owner = shopper("secure");
        long sku = product("secure", 5, "12.34");
        var cart = add(owner, sku, 2);
        long item = cart.path("items").get(0).path("id").asLong();
        var input = orderInput(owner, cart, List.of(item), "secure-order");
        assertThat(post(HttpClient.newBuilder().cookieHandler(new CookieManager()).build(), "/api/orders", input).statusCode()).isEqualTo(401);
        assertThat(raw(owner, "/api/orders", json.writeValueAsString(input), "invalid").statusCode()).isEqualTo(403);
        for (String field : List.of("user_id", "price", "quantity", "items", "total")) {
            var forged = new LinkedHashMap<>(input);
            forged.put(field, 1);
            assertThat(code(post(owner, "/api/orders", forged))).isEqualTo("INVALID_ORDER_INPUT");
        }
        String body = json.writeValueAsString(input);
        assertThat(code(raw(owner, "/api/orders", body.replace("\"revision\":1", "\"revision\":1,\"revision\":1"),
                data(get(owner, "/api/auth/csrf")).path("token").asText()))).isEqualTo("INVALID_ORDER_INPUT");
        var other = shopper("foreign");
        add(other, sku, 1);
        assertThat(post(other, "/api/orders", input).statusCode()).isEqualTo(404);
        var mixed = new LinkedHashMap<>(input);
        mixed.put("configurationIds", List.of(1));
        assertThat(code(post(owner, "/api/orders", mixed))).isEqualTo("MIXED_ORDER_NOT_SUPPORTED");
        db.update("UPDATE products SET price=12.35,revision=revision+1 WHERE id=?", sku);
        assertThat(code(post(owner, "/api/orders", input))).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
        var quote = orderInput(owner, cart, List.of(item), "secure-order");
        db.update("UPDATE products SET price=12.36,revision=revision+1 WHERE id=?", sku);
        assertThat(code(post(owner, "/api/orders", quote))).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
        var current = orderInput(owner, cart, List.of(item), "secure-order");
        post(owner, "/api/cart/items/" + item + "/quantity", Map.of("quantity", 1));
        assertThat(code(post(owner, "/api/orders", current))).isEqualTo("CART_CONFLICT");
        current = orderInput(owner, data(get(owner, "/api/cart")), List.of(item), "secure-order");
        var success = post(owner, "/api/orders", current);
        assertThat(success.statusCode()).as(success.body()).isEqualTo(200);
        String number = data(success).path("orderNumber").asText();
        assertThat(get(other, "/api/orders/" + number).statusCode()).isEqualTo(404);
        var reused = new LinkedHashMap<>(current);
        reused.put("revision", 99);
        assertThat(code(post(owner, "/api/orders", reused))).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test void faultsAtEveryWriteStageRollBackAllStockCartAndRetryEffects() throws Exception {
        var owner = shopper("fault");
        long first = product("faultA", 5, "10.01");
        long second = product("faultB", 5, "20.02");
        add(owner, first, 1);
        var cart = add(owner, second, 2);
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong(), cart.path("items").get(1).path("id").asLong()), "fault-order");
        long account = data(get(owner, "/api/auth/me")).path("id").asLong();
        var faults = Map.of("mall_order", "account_id<>" + account, "order_item", "product_id<>" + second,
                "stock_reservation_items", "product_id<>" + second, "order_request", "account_id<>" + account,
                "shopping_cart", "account_id<>" + account + " OR revision<3");
        for (var fault : faults.entrySet()) {
            String name = "fault16_" + prefix;
            db.execute("ALTER TABLE " + fault.getKey() + " ADD CONSTRAINT " + name + " CHECK (" + fault.getValue() + ")");
            try {
                var failed = post(owner, "/api/orders", input);
                assertThat(failed.statusCode()).as(fault.getKey() + ": " + failed.body()).isEqualTo(503);
                assertThat(data(get(owner, "/api/cart"))).isEqualTo(cart);
                assertThat(data(get(owner, "/api/products/" + first)).path("product").path("stock").asInt()).isEqualTo(5);
                assertThat(data(get(owner, "/api/products/" + second)).path("product").path("stock").asInt()).isEqualTo(5);
            } finally { db.execute("ALTER TABLE " + fault.getKey() + " DROP CHECK " + name); }
        }
        String guard = "delete_guard_" + prefix;
        db.execute("ALTER TABLE shopping_cart ADD COLUMN " + guard + " BIGINT NULL, ADD CONSTRAINT " + guard + " FOREIGN KEY(" + guard + ") REFERENCES shopping_cart_item(id)");
        try {
            db.update("UPDATE shopping_cart SET " + guard + "=? WHERE account_id=?", cart.path("items").get(1).path("id").asLong(), account);
            assertThat(post(owner, "/api/orders", input).statusCode()).isEqualTo(503);
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(cart);
            assertThat(data(get(owner, "/api/products/" + first)).path("product").path("stock").asInt()).isEqualTo(5);
        } finally { db.execute("ALTER TABLE shopping_cart DROP FOREIGN KEY " + guard + ", DROP COLUMN " + guard); }
        var success = post(owner, "/api/orders", input);
        assertThat(success.statusCode()).as(success.body()).isEqualTo(200);
        assertThat(data(success).path("total").asText()).isEqualTo("50.05");
        assertThat(data(post(owner, "/api/orders", input))).isEqualTo(data(success));
        assertThat(data(get(owner, "/api/products/" + first)).path("product").path("stock").asInt()).isEqualTo(4);
        assertThat(data(get(owner, "/api/products/" + second)).path("product").path("stock").asInt()).isEqualTo(3);
    }

    @Test void waitsForCommittedCatalogWritesAndRejectsTheOldQuoteWithoutHoldingPartialStock() throws Exception {
        var owner = shopper("wait");
        long sku = product("wait", 5, "10.01");
        var cart = add(owner, sku, 1);
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), "waiting-order");
        String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
        var executor = Executors.newSingleThreadExecutor();
        try (var writer = source.getConnection()) {
            writer.setAutoCommit(false);
            try {
                try (var statement = writer.prepareStatement("UPDATE products SET price=10.02,revision=revision+1 WHERE id=?")) {
                    statement.setLong(1, sku);
                    statement.executeUpdate();
                }
                var started = new CountDownLatch(1);
                var creating = executor.submit(() -> { started.countDown(); return raw(owner, "/api/orders", json.writeValueAsString(input), token); });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> creating.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                writer.commit();
                assertThat(code(creating.get(10, TimeUnit.SECONDS))).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
                assertThat(data(get(owner, "/api/cart")).path("items")).hasSize(1);
                var product = data(get(owner, "/api/products/" + sku)).path("product");
                assertThat(product.path("stock").asInt()).isEqualTo(5);
                assertThat(product.path("price").asText()).isEqualTo("10.02");
            } finally { writer.rollback(); }
        } finally { executor.shutdownNow(); }
    }

    @Test void simultaneousFirstSubmissionsWithOneKeyCreateOnlyOneOrder() throws Exception {
        var owner = shopper("double");
        long sku = product("double", 2, "10.01");
        var cart = add(owner, sku, 1);
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), "double-order");
        String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
        var barrier = new CyclicBarrier(3);
        var executor = Executors.newFixedThreadPool(3);
        try {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int index = 0; index < 3; index++) futures.add(executor.submit(() -> {
                barrier.await(); return raw(owner, "/api/orders", json.writeValueAsString(input), token);
            }));
            JsonNode first = null;
            for (var future : futures) {
                var response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                if (first == null) first = data(response);
                else assertThat(data(response)).isEqualTo(first);
            }
            assertThat(data(get(owner, "/api/products/" + sku)).path("product").path("stock").asInt()).isEqualTo(1);
            assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
        } finally { executor.shutdownNow(); }
    }

    @Test void administrationAndCheckoutCannotSpendTheSameAvailableStock() throws Exception {
        var owner = shopper("adminRace");
        var admin = account("admin", "ADMIN");
        long sku = product("adminRace", 2, "10.01");
        var cart = add(owner, sku, 2);
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), "admin-race");
        String ownerToken = data(get(owner, "/api/auth/csrf")).path("token").asText();
        String adminToken = data(get(admin, "/api/auth/csrf")).path("token").asText();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var purchasing = executor.submit(() -> { barrier.await(); return raw(owner, "/api/orders", json.writeValueAsString(input), ownerToken); });
            var adjusting = executor.submit(() -> { barrier.await(); return raw(admin, "/api/admin/products/" + sku + "/stock",
                    "{\"delta\":-1,\"reason\":\"并发库存验收\"}", adminToken); });
            var order = purchasing.get(10, TimeUnit.SECONDS);
            var adjustment = adjusting.get(10, TimeUnit.SECONDS);
            assertThat(List.of(order.statusCode(), adjustment.statusCode())).containsExactlyInAnyOrder(200, 409);
            var balance = data(get(admin, "/api/admin/products/" + sku));
            assertThat(balance.path("product").path("stock").asInt()).isEqualTo(order.statusCode() == 200 ? 0 : 1);
            assertThat(balance.path("reservedStock").asInt()).isEqualTo(order.statusCode() == 200 ? 2 : 0);
            assertThat(data(get(owner, "/api/cart")).path("items").size()).isEqualTo(order.statusCode() == 200 ? 0 : 1);
        } finally { executor.shutdownNow(); }
    }

    @Test void concurrentQuantityEditEitherPrecedesCheckoutOrFindsTheAlreadyPurchasedLineMissing() throws Exception {
        var owner = shopper("cartRace");
        long sku = product("cartRace", 3, "10.01");
        var cart = add(owner, sku, 1);
        long item = cart.path("items").get(0).path("id").asLong();
        var input = orderInput(owner, cart, List.of(item), "cart-race");
        String token = data(get(owner, "/api/auth/csrf")).path("token").asText();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var purchasing = executor.submit(() -> { barrier.await(); return raw(owner, "/api/orders", json.writeValueAsString(input), token); });
            var editing = executor.submit(() -> { barrier.await(); return raw(owner, "/api/cart/items/" + item + "/quantity", "{\"quantity\":2}", token); });
            var order = purchasing.get(10, TimeUnit.SECONDS);
            var edit = editing.get(10, TimeUnit.SECONDS);
            var remaining = data(get(owner, "/api/cart")).path("items");
            if (order.statusCode() == 200) {
                assertThat(edit.statusCode()).isEqualTo(404);
                assertThat(remaining).isEmpty();
                assertThat(data(get(owner, "/api/products/" + sku)).path("product").path("stock").asInt()).isEqualTo(2);
            } else {
                assertThat(code(order)).isEqualTo("CART_CONFLICT");
                assertThat(edit.statusCode()).isEqualTo(200);
                assertThat(remaining.get(0).path("quantity").asInt()).isEqualTo(2);
                assertThat(data(get(owner, "/api/products/" + sku)).path("product").path("stock").asInt()).isEqualTo(3);
            }
        } finally { executor.shutdownNow(); }
    }

    private HttpClient shopper(String suffix) throws Exception { return account(suffix, "USER"); }

    private HttpClient account(String suffix, String role) throws Exception {
        String name = prefix + suffix;
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES(?,?,?,?)", name, passwords.encode("OrderPassword123!"), name, role);
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertThat(post(client, "/api/auth/login", Map.of("username", name, "password", "OrderPassword123!")).statusCode()).isEqualTo(200);
        return client;
    }

    private long product(String suffix, int stock, String price) {
        db.update("""
            INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
            VALUES(?,'PSU',?,'测试品牌','Order-750','任务16夹具','/images/psu.svg',?,?,'ON_SALE','https://example.com/psu','测试夹具','test')
            """, prefix + suffix, "下单夹具 " + suffix, price, stock);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, prefix + suffix);
        db.update("INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) VALUES(?,'订单夹具','[]','test',CURRENT_DATE)", id);
        return id;
    }

    private JsonNode add(HttpClient client, long product, int quantity) throws Exception {
        var response = post(client, "/api/cart/items", Map.of("productId", product, "quantity", quantity, "requestKey", UUID.randomUUID().toString()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return data(response);
    }

    private Map<String,Object> orderInput(HttpClient client, JsonNode cart, List<Long> items, String key) throws Exception {
        var input = new LinkedHashMap<String,Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", items);
        input.put("configurationIds", List.of());
        var response = post(client, "/api/cart/checkout-preview", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        input.put("quoteToken", data(response).path("quoteToken").asText());
        input.put("requestKey", key);
        return input;
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception {
        return raw(client, path, json.writeValueAsString(body), data(get(client, "/api/auth/csrf")).path("token").asText());
    }
    private HttpResponse<String> raw(HttpClient client, String path, String body, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    private String code(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("code").asText(); }
}
