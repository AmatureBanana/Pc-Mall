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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public HTTP behavior over isolated MySQL; SQL only arranges fixtures and injects faults. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationCartTest {
    private static final String PATH = "/api/cart/configurations";
    private static final String PASSWORD = "ConfigPassword123!";
    private static final List<String> CATEGORIES = List.of("CPU", "MOTHERBOARD", "GPU", "MEMORY", "SSD", "PSU", "CASE", "COOLER");
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource source;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t14" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    @AfterAll void cleanup() {
        var ids = db.queryForList("SELECT id FROM account WHERE username LIKE ?", Long.class, prefix + "%");
        for (long id : ids) {
            db.update("DELETE FROM cart_request WHERE account_id=?", id);
            db.update("DELETE g FROM cart_configuration g JOIN shopping_cart c ON c.id=g.cart_id WHERE c.account_id=?", id);
            db.update("DELETE i FROM shopping_cart_item i JOIN shopping_cart c ON c.id=i.cart_id WHERE c.account_id=?", id);
            db.update("DELETE FROM shopping_cart WHERE account_id=?", id);
        }
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }

    @Test void duplicateJsonFieldsCannotHideASecondSelectionOrForgedContent() throws Exception {
        var owner = shopper("json");
        var selections = configuration("json");
        String parts = json.writeValueAsString(selections);
        var response = rawPost(owner, PATH, "{\"selections\":[],\"selections\":" + parts + "}", csrf(owner));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("INVALID_CART_INPUT");
        assertThat(data(get(owner, "/api/cart")).path("configurations")).isEmpty();
    }

    @Test void duplicateConfigurationStillRevalidatesCurrentAvailabilityWithoutAddingDemand() throws Exception {
        var owner = shopper("recheck");
        var selections = configuration("recheck");
        long cpu = (long) selections.get(0).get("productId");
        db.update("UPDATE products SET stock=1 WHERE id=?", cpu);
        var request = Map.of("selections", selections);
        var first = post(owner, PATH, request);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        var original = data(first);
        assertThat(data(post(owner, PATH, request))).isEqualTo(original);
        db.update("UPDATE products SET stock=0 WHERE id=?", cpu);
        var refused = post(owner, PATH, request);
        assertThat(refused.statusCode()).as(refused.body()).isEqualTo(409);
        assertThat(code(refused)).isEqualTo("CART_STOCK_LIMIT");
        var retained = data(get(owner, "/api/cart"));
        assertThat(retained.path("revision")).isEqualTo(original.path("revision"));
        assertThat(retained.path("configurations")).hasSize(1);
        assertThat(retained.path("configurations").get(0).path("id")).isEqualTo(original.path("configurations").get(0).path("id"));
    }

    @Test void configurationWaitsForCatalogWriteAndValidatesItsCommittedStock() throws Exception {
        var owner = shopper("catalog");
        var selections = configuration("catalog");
        long cpu = (long) selections.get(0).get("productId");
        String token = csrf(owner);
        var executor = Executors.newSingleThreadExecutor();
        try (var catalog = source.getConnection()) {
            catalog.setAutoCommit(false);
            try {
                try (var update = catalog.prepareStatement("UPDATE products SET stock=0,revision=revision+1 WHERE id=?")) {
                    update.setLong(1, cpu);
                    update.executeUpdate();
                }
                var started = new CountDownLatch(1);
                var adding = executor.submit(() -> {
                    started.countDown();
                    return rawPost(owner, PATH, json.writeValueAsString(Map.of("selections", selections)), token);
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                // The catalog transaction is deliberately held open, not released by a timing sleep.
                assertThatThrownBy(() -> adding.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                catalog.commit();
                var refused = adding.get(10, TimeUnit.SECONDS);
                assertThat(refused.statusCode()).as(refused.body()).isEqualTo(409);
                assertThat(code(refused)).isEqualTo("CART_STOCK_LIMIT");
                assertThat(data(get(owner, "/api/cart")).path("configurations")).isEmpty();
            } finally { catalog.rollback(); }
        } finally { executor.shutdownNow(); }
    }

    @Test void eighthItemWriteFailureRollsBackTheWholeGroupAndAllowsRetry() throws Exception {
        var owner = shopper("rollback");
        var selections = configuration("rollback");
        long cpu = (long) selections.get(0).get("productId");
        long eighth = (long) selections.get(7).get("productId");
        var before = data(post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 1, "requestKey", "rollback-single")));
        var inventory = inventory("rollback");
        String constraint = "fault_" + prefix;
        db.execute("ALTER TABLE cart_configuration_item ADD CONSTRAINT " + constraint + " CHECK (product_id <> " + eighth + ")");
        try {
            var failed = post(owner, PATH, Map.of("selections", selections));
            assertThat(failed.statusCode()).as(failed.body()).isEqualTo(503);
            assertThat(code(failed)).isEqualTo("CART_UNAVAILABLE");
            assertThat(failed.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(before);
            assertThat(inventory("rollback")).isEqualTo(inventory);
        } finally { db.execute("ALTER TABLE cart_configuration_item DROP CHECK " + constraint); }
        var retry = post(owner, PATH, Map.of("selections", selections));
        assertThat(retry.statusCode()).as(retry.body()).isEqualTo(200);
        assertThat(data(retry).path("configurations")).hasSize(1);
        assertThat(data(retry).path("configurations").get(0).path("items")).hasSize(8);
        assertThat(data(retry).path("items")).hasSize(1);
        assertThat(inventory("rollback")).isEqualTo(inventory);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservation_items i JOIN products p ON p.id=i.product_id WHERE p.sku LIKE ?",
                Integer.class, prefix + "rollback%")).isZero();
    }

    @Test void concurrentDuplicatesCreateOneGroupEvenWhenSubmittedInDifferentOrders() throws Exception {
        var owner = shopper("parallel");
        var selections = configuration("parallel");
        var reversed = new ArrayList<>(selections);
        Collections.reverse(reversed);
        String token = csrf(owner);
        var executor = Executors.newFixedThreadPool(4);
        var start = new CyclicBarrier(4);
        try {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int index = 0; index < 4; index++) {
                var body = json.writeValueAsString(Map.of("selections", index % 2 == 0 ? selections : reversed));
                futures.add(executor.submit(() -> { start.await(); return rawPost(owner, PATH, body, token); }));
            }
            var first = data(futures.get(0).get(15, TimeUnit.SECONDS));
            for (var future : futures) {
                var response = future.get(15, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(data(response)).isEqualTo(first);
            }
            assertThat(first.path("configurations")).hasSize(1);
            assertThat(first.path("configurations").get(0).path("items")).hasSize(8);
            assertThat(first.path("revision").asLong()).isEqualTo(1);
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(first);
        } finally { executor.shutdownNow(); }
    }

    @Test void singleItemsAndDifferentGroupsKeepTheirIdentityAndAggregateBeyondNinetyNine() throws Exception {
        var owner = shopper("mixed");
        var selections = configuration("mixed");
        long cpu = (long) selections.get(0).get("productId");
        db.update("UPDATE products SET stock=101 WHERE id=?", cpu);
        assertThat(post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 99, "requestKey", "mixed-single")).statusCode()).isEqualTo(200);
        assertThat(post(owner, PATH, Map.of("selections", selections)).statusCode()).isEqualTo(200);
        var twoGroups = post(owner, PATH, Map.of("selections", selections, "memoryQuantity", 4));
        assertThat(twoGroups.statusCode()).as(twoGroups.body()).isEqualTo(200);
        var mixed = data(twoGroups);
        assertThat(mixed.path("items")).hasSize(1);
        assertThat(mixed.path("items").get(0).path("quantity").asInt()).isEqualTo(99);
        assertThat(mixed.path("configurations")).hasSize(2);
        assertThat(mixed.path("configurations").get(0).path("amount").asText()).isEqualTo("10102.50");
        assertThat(mixed.path("configurations").get(1).path("amount").asText()).isEqualTo("11451.00");
        assertThat(mixed.path("total").asText()).isEqualTo("169954.50");
        var refused = post(owner, PATH, Map.of("selections", selections, "memoryQuantity", 2));
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(code(refused)).isEqualTo("CART_STOCK_LIMIT");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(mixed);
        long singleId = mixed.path("items").get(0).path("id").asLong();
        assertThat(delete(owner, "/api/cart/items/" + singleId).statusCode()).isEqualTo(200);
        assertThat(data(get(owner, "/api/cart")).path("configurations")).hasSize(2);
        assertThat(post(owner, PATH, Map.of("selections", selections, "memoryQuantity", 2)).statusCode()).isEqualTo(200);
    }

    @Test void aggregateDemandProtectsEveryAddAndQuantityChangeUnderConcurrency() throws Exception {
        var owner = shopper("stock");
        var selections = configuration("stock");
        long cpu = (long) selections.get(0).get("productId");
        db.update("UPDATE products SET stock=1 WHERE id=?", cpu);
        String token = csrf(owner);
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var group = executor.submit(() -> { start.await(); return rawPost(owner, PATH,
                    json.writeValueAsString(Map.of("selections", selections)), token); });
            var single = executor.submit(() -> { start.await(); return rawPost(owner, "/api/cart/items",
                    json.writeValueAsString(Map.of("productId", cpu, "quantity", 1, "requestKey", "stock-single")), token); });
            var responses = List.of(group.get(15, TimeUnit.SECONDS), single.get(15, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 409);
            for (var response : responses) if (response.statusCode() == 409) assertThat(code(response)).isEqualTo("CART_STOCK_LIMIT");
            var cart = data(get(owner, "/api/cart"));
            assertThat(cart.path("lineCount").asLong()).isEqualTo(1);
        } finally { executor.shutdownNow(); }

        db.update("UPDATE products SET stock=3 WHERE id=?", cpu);
        assertThat(post(owner, PATH, Map.of("selections", selections)).statusCode()).isEqualTo(200);
        var added = post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 1, "requestKey", "stock-second"));
        assertThat(added.statusCode()).isEqualTo(200);
        var before = data(added);
        long itemId = before.path("items").get(0).path("id").asLong();
        var refused = post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 3));
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(code(refused)).isEqualTo("CART_STOCK_LIMIT");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(before);
    }

    @Test void sevenCategoryIntegratedBuildPersistsAcrossLoginAndCanOnlyBeRemovedAsAWhole() throws Exception {
        var owner = shopper("seven");
        var selections = configuration("seven", true);
        selections.removeIf(part -> part.get("category").equals("GPU"));
        long cpu = (long) selections.get(0).get("productId");
        var request = Map.of("selections", selections, "integratedGraphicsCpuId", cpu, "memoryQuantity", 4);
        var response = post(owner, PATH, request);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var original = data(response);
        assertThat(original.path("configurations").get(0).path("items")).hasSize(7);
        assertThat(original.path("total").asText()).isEqualTo("6552.00");
        long groupId = original.path("configurations").get(0).path("id").asLong();
        assertThat(post(owner, "/api/auth/logout", Map.of()).statusCode()).isEqualTo(200);
        owner = shopper("seven");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(original);
        var other = shopper("other");
        assertThat(data(get(other, "/api/cart")).path("configurations")).isEmpty();
        assertThat(delete(other, PATH + "/" + groupId).statusCode()).isEqualTo(404);
        var forged = new LinkedHashMap<String, Object>(request);
        forged.put("user_id", 1);
        assertThat(code(post(other, PATH, forged))).isEqualTo("INVALID_CART_INPUT");
        long memberId = original.path("configurations").get(0).path("items").get(0).path("id").asLong();
        var changeMember = post(owner, "/api/cart/items/" + memberId + "/quantity", Map.of("quantity", 2));
        assertThat(changeMember.statusCode()).isEqualTo(404);
        assertThat(code(changeMember)).isEqualTo("CART_ITEM_NOT_FOUND");
        var removeMember = delete(owner, "/api/cart/items/" + memberId);
        assertThat(removeMember.statusCode()).isEqualTo(404);
        assertThat(code(removeMember)).isEqualTo("CART_ITEM_NOT_FOUND");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(original);
        var removed = delete(owner, PATH + "/" + groupId);
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(data(removed).path("configurations")).isEmpty();
    }

    @Test void currentParametersAndPricesAreRevalidatedAndCannotChangeTheDuplicateIdentity() throws Exception {
        var owner = shopper("current");
        var selections = configuration("current");
        long cpu = (long) selections.get(0).get("productId");
        var request = Map.of("selections", selections);
        var initial = data(post(owner, PATH, request));
        db.update("UPDATE products SET name='新名称',price=1500.01,revision=revision+1 WHERE id=?", cpu);
        var duplicate = post(owner, PATH, request);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        var current = data(duplicate);
        assertThat(current.path("configurations")).hasSize(1);
        assertThat(current.path("configurations").get(0).path("id")).isEqualTo(initial.path("configurations").get(0).path("id"));
        assertThat(current.path("revision")).isEqualTo(initial.path("revision"));
        assertThat(current.path("total").asText()).isEqualTo("10103.51");
        db.update("UPDATE product_specifications SET parameters='[]' WHERE product_id=?", cpu);
        var unknown = post(owner, PATH, request);
        assertThat(unknown.statusCode()).isEqualTo(409);
        assertThat(code(unknown)).isEqualTo("CONFIGURATION_NOT_COMPATIBLE");
        assertThat(data(get(owner, "/api/cart")).path("configurations")).hasSize(1);
    }

    @Test void guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups() throws Exception {
        var owner = shopper("invalid");
        var selections = configuration("invalid");
        var request = Map.of("selections", selections);
        assertThat(post(browser(), PATH, request).statusCode()).isEqualTo(401);
        assertThat(delete(browser(), PATH + "/1").statusCode()).isEqualTo(401);
        assertThat(rawPost(owner, PATH, json.writeValueAsString(request), "missing-token").statusCode()).isEqualTo(403);
        for (String field : List.of("corePassed", "price", "name", "source", "accountId")) {
            var forged = new LinkedHashMap<String, Object>(request);
            forged.put(field, "forged");
            assertThat(code(post(owner, PATH, forged))).as(field).isEqualTo("INVALID_CART_INPUT");
        }
        var repeatedCategory = new ArrayList<>(selections);
        repeatedCategory.set(7, selections.get(0));
        assertThat(code(post(owner, PATH, Map.of("selections", repeatedCategory)))).isEqualTo("INVALID_CART_INPUT");
        assertThat(code(post(owner, PATH, Map.of("selections", selections.subList(0, 6))))).isEqualTo("INVALID_CART_INPUT");
        var noCooler = selections.subList(0, 7);
        assertThat(code(post(owner, PATH, Map.of("selections", noCooler)))).isEqualTo("CONFIGURATION_NOT_COMPATIBLE");
        for (Object quantity : List.of(0, 5, 1.5, "2")) {
            assertThat(code(post(owner, PATH, Map.of("selections", selections, "memoryQuantity", quantity)))).isEqualTo("INVALID_CART_INPUT");
        }
        var mismatched = new ArrayList<>(selections);
        mismatched.set(0, Map.of("category", "CPU", "productId", selections.get(1).get("productId")));
        assertThat(code(post(owner, PATH, Map.of("selections", mismatched)))).isEqualTo("INVALID_CART_INPUT");
        long cpu = (long) selections.get(0).get("productId");
        db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", cpu);
        assertThat(code(post(owner, PATH, request))).isEqualTo("CART_STOCK_LIMIT");
        assertThat(data(get(owner, "/api/cart")).path("configurations")).isEmpty();
        assertThat(data(get(owner, "/api/cart")).path("revision").asLong()).isZero();
    }

    /** Clone known compatible catalog references into private fixture SKUs; never mutate demo rows. */
    private List<Map<String, Object>> configuration(String suffix) {
        return configuration(suffix, false);
    }

    private List<Map<String, Object>> configuration(String suffix, boolean integrated) {
        long memory = db.queryForObject("SELECT id FROM products WHERE sku='KINGSTON-KF560C36BBE2-16'", Long.class);
        long cpu = integrated ? db.queryForObject("SELECT id FROM products WHERE sku='INTEL-I5-14600K'", Long.class) : 1L;
        List<Long> templates = List.of(cpu, 3L, 5L, memory, 9L, 11L, 12L, 8L);
        var parts = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < CATEGORIES.size(); index++) {
            String sku = prefix + suffix + index;
            long template = templates.get(index);
            db.update("""
                INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
                SELECT ?,category,name,brand,model,summary,image_url,price,200,'ON_SALE',source_url,price_note,data_version
                FROM products WHERE id=?
                """, sku, template);
            long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
            db.update("""
                INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on)
                SELECT ?,description,parameters,data_version,verified_on FROM product_specifications WHERE product_id=?
                """, id, template);
            parts.add(Map.of("category", CATEGORIES.get(index), "productId", id));
        }
        return parts;
    }

    private List<Map<String, Object>> inventory(String suffix) {
        return db.queryForList("SELECT id,stock,reserved_stock FROM products WHERE sku LIKE ? ORDER BY id", prefix + suffix + "%");
    }

    private HttpClient shopper(String suffix) throws Exception {
        String username = prefix + suffix;
        db.update("INSERT IGNORE INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'USER')",
                username, passwords.encode(PASSWORD), username);
        var client = browser();
        assertThat(post(client, "/api/auth/login", Map.of("username", username, "password", PASSWORD)).statusCode()).isEqualTo(200);
        return client;
    }

    private HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, Object input) throws Exception {
        return rawPost(client, path, json.writeValueAsString(input), csrf(client));
    }
    private HttpResponse<String> delete(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).header("X-CSRF-TOKEN", csrf(client))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> rawPost(HttpClient client, String path, String input, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(input)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String csrf(HttpClient client) throws Exception { return data(get(client, "/api/auth/csrf")).path("token").asText(); }
    private JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    private String code(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("code").asText(); }
    private String base() { return "http://127.0.0.1:" + port; }
}
