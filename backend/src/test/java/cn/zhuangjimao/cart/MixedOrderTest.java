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
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public HTTP is the assertion boundary; SQL only arranges private fixtures and faults. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MixedOrderTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource source;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "o17" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    @AfterAll void cleanup() {
        var references = db.queryForList("SELECT DISTINCT i.reference FROM stock_reservation_items i JOIN products p ON p.id=i.product_id WHERE p.sku LIKE ?", String.class, prefix + "%");
        db.update("DELETE r FROM order_request r JOIN account a ON a.id=r.account_id WHERE a.username LIKE ?", prefix + "%");
        db.update("DELETE i FROM order_item i JOIN mall_order o ON o.order_number=i.order_number JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
        db.update("DELETE o FROM mall_order o JOIN account a ON a.id=o.account_id WHERE a.username LIKE ?", prefix + "%");
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
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }

    @Test void ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership() throws Exception {
        var owner = shopper("happy");
        var parts = configuration("happy");
        long cpu = (long) parts.get(0).get("productId");
        addGroup(owner, parts, 1);
        addGroup(owner, parts, 2);
        var cart = add(owner, cpu, 99);
        long single = cart.path("items").get(0).path("id").asLong();
        long first = cart.path("configurations").get(0).path("id").asLong();
        long second = cart.path("configurations").get(1).path("id").asLong();
        var input = orderInput(owner, cart, List.of(single), List.of(first, second), "mixed-happy");
        var response = post(owner, "/api/orders", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var order = data(response);
        assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("total").asText()).isEqualTo("169055.50");
        assertThat(order.path("items")).hasSize(17);
        assertThat(order.path("configurations")).hasSize(2);
        assertThat(order.path("configurations").get(0).path("id").asLong()).isEqualTo(first);
        assertThat(order.path("configurations").get(0).path("amount").asText()).isEqualTo("10102.50");
        assertThat(order.path("configurations").get(1).path("amount").asText()).isEqualTo("10552.00");
        assertThat(order.path("items").findValues("configurationId").stream().filter(JsonNode::isNull)).hasSize(1);
        assertThat(order.path("items").findValues("configurationId").stream().filter(id -> id.asLong() == first)).hasSize(8);
        assertThat(order.path("items").findValues("configurationId").stream().filter(id -> id.asLong() == second)).hasSize(8);
        assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(99);
        var remaining = data(get(owner, "/api/cart"));
        assertThat(remaining.path("items")).isEmpty();
        assertThat(remaining.path("configurations")).isEmpty();
        db.update("UPDATE products SET name='changed',model='changed',price=99.99,status='OFF_SALE',revision=revision+1 WHERE sku LIKE ?", prefix + "happy%");
        db.update("UPDATE product_specifications s JOIN products p ON p.id=s.product_id SET s.parameters=JSON_ARRAY() WHERE p.sku LIKE ?", prefix + "happy%");
        assertThat(data(get(owner, "/api/orders/" + order.path("orderNumber").asText()))).isEqualTo(order);
        assertThat(data(post(owner, "/api/orders", input))).isEqualTo(order);
        var reused = new LinkedHashMap<>(input);
        reused.put("configurationIds", List.of(first));
        assertThat(code(post(owner, "/api/orders", reused))).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test void finalAdmissionRejectsMalformedGroupQuantitiesEvenWithAFreshQuoteToken() throws Exception {
        var owner = shopper("quantity");
        var parts = configuration("quantity");
        var cart = addGroup(owner, parts, 1);
        long group = cart.path("configurations").get(0).path("id").asLong();
        long cpu = (long) parts.get(0).get("productId");
        db.update("UPDATE cart_configuration_item SET quantity=2 WHERE configuration_id=? AND product_id=?", group, cpu);
        var malformed = data(get(owner, "/api/cart"));
        assertThat(malformed.path("configurations").get(0).path("valid").asBoolean()).isFalse();
        var input = Map.of("revision", cart.path("revision").asLong(), "itemIds", List.of(), "configurationIds", List.of(group),
                "quoteToken", "0".repeat(64), "requestKey", "malformed-group");
        assertThat(code(post(owner, "/api/orders", input))).isEqualTo("CART_SELECTION_INVALID");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(malformed);
        assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(200);
    }

    @Test void anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder() throws Exception {
        var owner = shopper("admission");
        var parts = configuration("admission");
        long cpu = (long) parts.get(0).get("productId");
        long board = (long) parts.get(1).get("productId");
        long cooler = (long) parts.get(7).get("productId");
        addGroup(owner, parts, 1);
        addGroup(owner, parts, 2);
        var cart = add(owner, cpu, 99);
        long first = cart.path("configurations").get(0).path("id").asLong();
        long second = cart.path("configurations").get(1).path("id").asLong();
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), List.of(first, second), "final-admission");
        String parameters = db.queryForObject("SELECT parameters FROM product_specifications WHERE product_id=?", String.class, board);
        var mutations = List.of(
                "DELETE FROM cart_configuration_item WHERE configuration_id=" + second + " AND product_id=" + cooler,
                "UPDATE product_specifications SET parameters=JSON_ARRAY() WHERE product_id=" + board,
                "UPDATE product_specifications s JOIN product_specifications t ON t.product_id=4 SET s.parameters=t.parameters WHERE s.product_id=" + board,
                "UPDATE products SET status='OFF_SALE' WHERE id=" + cooler,
                "UPDATE products SET stock=0 WHERE id=" + cooler,
                "UPDATE products SET stock=100 WHERE id=" + cpu);
        for (String mutation : mutations) {
            db.execute(mutation);
            var before = data(get(owner, "/api/cart"));
            assertThat(code(post(owner, "/api/orders", input))).as(mutation).isEqualTo("CART_SELECTION_INVALID");
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(before);
            assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(mutation.contains("stock=100") ? 100 : 200);
            db.update("INSERT IGNORE INTO cart_configuration_item(configuration_id,product_id,quantity) VALUES(?,?,1)", second, cooler);
            db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", parameters, board);
            db.update("UPDATE products SET status='ON_SALE',stock=200 WHERE id IN (?,?)", cpu, cooler);
        }
        db.update("UPDATE products SET price=price+0.01,revision=revision+1 WHERE id=?", cooler);
        assertThat(code(post(owner, "/api/orders", input))).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
        input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), List.of(first, second), "final-admission");
        db.update("UPDATE products SET price=price+0.01,revision=revision+1 WHERE id=?", cooler);
        assertThat(code(post(owner, "/api/orders", input))).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
        input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), List.of(first, second), "final-admission");
        assertThat(post(owner, "/api/orders", input).statusCode()).isEqualTo(200);
        assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(99);
    }

    @Test void failuresDuringGroupWritesAndRemovalRollBackEverySelectedEntry() throws Exception {
        var owner = shopper("fault");
        var parts = configuration("fault");
        long cpu = (long) parts.get(0).get("productId");
        addGroup(owner, parts, 1);
        addGroup(owner, parts, 2);
        var cart = add(owner, cpu, 1);
        long first = cart.path("configurations").get(0).path("id").asLong();
        long second = cart.path("configurations").get(1).path("id").asLong();
        long account = data(get(owner, "/api/auth/me")).path("id").asLong();
        var input = orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()), List.of(first, second), "mixed-fault");
        var faults = Map.of("order_configuration", "configuration_id<>" + second,
                "order_item", "configuration_id IS NULL OR configuration_id<>" + second + " OR product_id<>" + cpu,
                "stock_reservation_items", "product_id<>" + parts.get(7).get("productId"),
                "order_request", "account_id<>" + account,
                "shopping_cart", "account_id<>" + account + " OR revision<4");
        for (var fault : faults.entrySet()) {
            String constraint = "fault17_" + prefix;
            db.execute("ALTER TABLE " + fault.getKey() + " ADD CONSTRAINT " + constraint + " CHECK (" + fault.getValue() + ")");
            try {
                assertThat(post(owner, "/api/orders", input).statusCode()).as(fault.getKey()).isEqualTo(503);
                assertThat(data(get(owner, "/api/cart"))).isEqualTo(cart);
                for (var part : parts) {
                    assertThat(data(get(owner, "/api/products/" + part.get("productId"))).path("product").path("stock").asInt()).isEqualTo(200);
                }
            } finally { db.execute("ALTER TABLE " + fault.getKey() + " DROP CHECK " + constraint); }
        }
        String guard = "delete_guard_" + prefix;
        db.execute("ALTER TABLE shopping_cart ADD COLUMN " + guard + " BIGINT NULL, ADD CONSTRAINT " + guard + " FOREIGN KEY(" + guard + ") REFERENCES cart_configuration(id)");
        try {
            db.update("UPDATE shopping_cart SET " + guard + "=? WHERE account_id=?", second, account);
            assertThat(post(owner, "/api/orders", input).statusCode()).isEqualTo(503);
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(cart);
            assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(200);
        } finally { db.execute("ALTER TABLE shopping_cart DROP FOREIGN KEY " + guard + ", DROP COLUMN " + guard); }
        var created = post(owner, "/api/orders", input);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        assertThat(data(post(owner, "/api/orders", input))).isEqualTo(data(created));
        assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(197);
    }

    @Test void wholeGroupOwnershipIsRequiredAndUnselectedInvalidEntriesRemain() throws Exception {
        var owner = shopper("security");
        var stranger = shopper("stranger");
        var parts = configuration("security");
        var cart = addGroup(owner, parts, 1);
        long group = cart.path("configurations").get(0).path("id").asLong();
        var input = orderInput(owner, cart, List.of(), List.of(group), "whole-security");
        addGroup(stranger, parts, 1);
        assertThat(post(stranger, "/api/orders", input).statusCode()).isEqualTo(404);
        for (String field : List.of("configurationItems", "productIds", "source", "corePassed", "quantity")) {
            var forged = new LinkedHashMap<>(input);
            forged.put(field, List.of(parts.get(0)));
            assertThat(code(post(owner, "/api/orders", forged))).isEqualTo("INVALID_ORDER_INPUT");
        }
        var split = new LinkedHashMap<>(input);
        split.put("configurationIds", List.of());
        split.put("itemIds", List.of(parts.get(0).get("productId")));
        assertThat(post(owner, "/api/orders", split).statusCode()).isEqualTo(404);
        var other = configuration("unselected");
        cart = addGroup(owner, other, 1);
        long invalid = cart.path("configurations").get(1).path("id").asLong();
        cart = add(owner, (long) other.get(0).get("productId"), 1);
        db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", other.get(0).get("productId"));
        cart = data(get(owner, "/api/cart"));
        input = orderInput(owner, cart, List.of(), List.of(group), "whole-security");
        var success = post(owner, "/api/orders", input);
        assertThat(success.statusCode()).as(success.body()).isEqualTo(200);
        var remaining = data(get(owner, "/api/cart"));
        assertThat(remaining.path("items")).hasSize(1);
        assertThat(remaining.path("configurations")).hasSize(1);
        assertThat(remaining.path("configurations").get(0).path("id").asLong()).isEqualTo(invalid);
        assertThat(remaining.path("configurations").get(0).path("valid").asBoolean()).isFalse();
        assertThat(get(stranger, "/api/orders/" + data(success).path("orderNumber").asText()).statusCode()).isEqualTo(404);
    }

    @Test void competingMixedOrdersNeverOversellAndConcurrentRetriesReturnTheOriginalGroups() throws Exception {
        var parts = configuration("race");
        long cpu = (long) parts.get(0).get("productId");
        db.update("UPDATE products SET stock=2 WHERE sku LIKE ?", prefix + "race%");
        var left = shopper("left");
        var right = shopper("right");
        var requests = new ArrayList<Map<String,Object>>();
        for (var owner : List.of(left, right)) {
            addGroup(owner, parts, 1);
            var cart = add(owner, cpu, 1);
            requests.add(orderInput(owner, cart, List.of(cart.path("items").get(0).path("id").asLong()),
                    List.of(cart.path("configurations").get(0).path("id").asLong()), "mixed-race"));
        }
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { barrier.await(); return post(left, "/api/orders", requests.get(0)); });
            var b = executor.submit(() -> { barrier.await(); return post(right, "/api/orders", requests.get(1)); });
            var responses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 409);
            int winner = responses.get(0).statusCode() == 200 ? 0 : 1;
            var client = winner == 0 ? left : right;
            var original = data(responses.get(winner));
            var retryBarrier = new CyclicBarrier(2);
            var retryA = executor.submit(() -> { retryBarrier.await(); return post(client, "/api/orders", requests.get(winner)); });
            var retryB = executor.submit(() -> { retryBarrier.await(); return post(client, "/api/orders", requests.get(winner)); });
            assertThat(data(retryA.get(10, TimeUnit.SECONDS))).isEqualTo(original);
            assertThat(data(retryB.get(10, TimeUnit.SECONDS))).isEqualTo(original);
            assertThat(data(get(client, "/api/products/" + cpu)).path("product").path("stock").asInt()).isZero();
            var loser = data(get(winner == 0 ? right : left, "/api/cart"));
            assertThat(loser.path("items")).hasSize(1);
            assertThat(loser.path("configurations")).hasSize(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void finalValidationWaitsForAParameterWriterThenRejectsItsUnverifiableBuild() throws Exception {
        var owner = shopper("writer");
        var parts = configuration("writer");
        long cpu = (long) parts.get(0).get("productId");
        long board = (long) parts.get(1).get("productId");
        var cart = addGroup(owner, parts, 1);
        var input = orderInput(owner, cart, List.of(), List.of(cart.path("configurations").get(0).path("id").asLong()), "parameter-writer");
        var executor = Executors.newSingleThreadExecutor();
        try (var writer = source.getConnection()) {
            writer.setAutoCommit(false);
            try {
                try (var lock = writer.prepareStatement("UPDATE products SET revision=revision+1 WHERE id=?")) {
                    lock.setLong(1, board); lock.executeUpdate();
                }
                try (var update = writer.prepareStatement("UPDATE product_specifications SET parameters=JSON_ARRAY() WHERE product_id=?")) {
                    update.setLong(1, board); update.executeUpdate();
                }
                var started = new CountDownLatch(1);
                var creating = executor.submit(() -> { started.countDown(); return post(owner, "/api/orders", input); });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> creating.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                writer.commit();
                assertThat(code(creating.get(10, TimeUnit.SECONDS))).isEqualTo("CART_SELECTION_INVALID");
                assertThat(data(get(owner, "/api/cart")).path("configurations")).hasSize(1);
                assertThat(data(get(owner, "/api/products/" + cpu)).path("product").path("stock").asInt()).isEqualTo(200);
            } finally { writer.rollback(); }
        } finally { executor.shutdownNow(); }
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
    private HttpClient shopper(String suffix) throws Exception {
        String name = prefix + suffix;
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES(?,?,?,'USER')", name, passwords.encode("OrderPassword123!"), name);
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertThat(post(client, "/api/auth/login", Map.of("username", name, "password", "OrderPassword123!")).statusCode()).isEqualTo(200);
        return client;
    }
    private JsonNode addGroup(HttpClient client, List<Map<String,Object>> parts, int memory) throws Exception {
        var response = post(client, "/api/cart/configurations", Map.of("selections", parts, "memoryQuantity", memory));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return data(response);
    }
    private JsonNode add(HttpClient client, long product, int quantity) throws Exception {
        var response = post(client, "/api/cart/items", Map.of("productId", product, "quantity", quantity, "requestKey", UUID.randomUUID().toString()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return data(response);
    }
    private Map<String,Object> orderInput(HttpClient client, JsonNode cart, List<Long> items, List<Long> groups, String key) throws Exception {
        var input = new LinkedHashMap<String,Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", items);
        input.put("configurationIds", groups);
        var quote = post(client, "/api/cart/checkout-preview", input);
        assertThat(quote.statusCode()).as(quote.body()).isEqualTo(200);
        input.put("quoteToken", data(quote).path("quoteToken").asText());
        input.put("requestKey", key);
        return input;
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, Object input) throws Exception {
        String token = data(get(client, "/api/auth/csrf")).path("token").asText();
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    private String code(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("code").asText(); }
}
