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
class CartCheckoutPreviewTest {
    private static final String PATH = "/api/cart/configurations";
    private static final String PASSWORD = "ConfigPassword123!";
    private static final List<String> CATEGORIES = List.of("CPU", "MOTHERBOARD", "GPU", "MEMORY", "SSD", "PSU", "CASE", "COOLER");
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource source;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t15" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

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

    @Test void configurationReplacementIsAtomicAndRejectsStaleEditors() throws Exception {
        var owner = shopper("edit");
        var selections = configuration("edit");
        var before = data(post(owner, PATH, Map.of("selections", selections)));
        var original = before.path("configurations").get(0);
        long id = original.path("id").asLong();
        var request = new LinkedHashMap<String, Object>();
        request.put("selections", selections);
        request.put("memoryQuantity", 2);
        request.put("revision", before.path("revision").asLong());
        request.put("editToken", original.path("editToken").asText());
        var replaced = post(owner, PATH + "/" + id, request);
        assertThat(replaced.statusCode()).as(replaced.body()).isEqualTo(200);
        var after = data(replaced);
        assertThat(after.path("revision").asLong()).isEqualTo(2);
        assertThat(after.path("configurations")).hasSize(1);
        assertThat(after.path("configurations").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(after.path("configurations").get(0).path("amount").asText()).isEqualTo("10552.00");
        var stale = post(owner, PATH + "/" + id, request);
        assertThat(stale.statusCode()).as(stale.body()).isEqualTo(409);
        assertThat(code(stale)).isEqualTo("CART_CONFLICT");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(after);
    }

    @Test void currentParameterChangesDiagnoseAndRetainIncompatibleAndUnverifiedGroups() throws Exception {
        var owner = shopper("diagnose");
        var selections = configuration("diagnose");
        var before = data(post(owner, PATH, Map.of("selections", selections)));
        long cpu = (long) selections.get(0).get("productId");
        long board = (long) selections.get(1).get("productId");
        db.update("UPDATE product_specifications SET parameters=JSON_ARRAY() WHERE product_id=?", board);
        var unknown = data(get(owner, "/api/cart"));
        var group = unknown.path("configurations").get(0);
        assertThat(group.path("state").asText()).isEqualTo("UNVERIFIED");
        assertThat(group.path("valid").asBoolean()).isFalse();
        assertThat(group.path("reasons").size()).isGreaterThan(0);
        assertThat(group.path("items")).hasSize(8);
        assertThat(unknown.path("revision")).isEqualTo(before.path("revision"));
        var staleEdit = Map.of("selections", selections, "memoryQuantity", 2, "revision", before.path("revision").asLong(),
                "editToken", before.path("configurations").get(0).path("editToken").asText());
        assertThat(code(post(owner, PATH + "/" + group.path("id").asLong(), staleEdit))).isEqualTo("CART_CONFLICT");
        db.update("UPDATE product_specifications s JOIN product_specifications template ON template.product_id=4 SET s.parameters=template.parameters WHERE s.product_id=?", board);
        var conflict = data(get(owner, "/api/cart")).path("configurations").get(0);
        assertThat(conflict.path("state").asText()).isEqualTo("INCOMPATIBLE");
        assertThat(conflict.path("reasons").toString()).contains("C01");
        var mixed = data(post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 1, "requestKey", "diagnose-single")));
        var onlySingle = post(owner, "/api/cart/checkout-preview", Map.of("revision", mixed.path("revision").asLong(),
                "itemIds", List.of(mixed.path("items").get(0).path("id").asLong()), "configurationIds", List.of()));
        assertThat(onlySingle.statusCode()).as(onlySingle.body()).isEqualTo(200);
        assertThat(data(onlySingle).path("total").asText()).isEqualTo("1499.00");
        db.update("UPDATE products SET stock=0 WHERE id=?", cpu);
        var unavailable = data(get(owner, "/api/cart")).path("configurations").get(0);
        assertThat(unavailable.path("state").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(unavailable.path("reasons").size()).isGreaterThan(1);
        db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", cpu);
        assertThat(data(get(owner, "/api/cart")).path("configurations").get(0).path("state").asText()).isEqualTo("OFF_SALE");
    }

    @Test void mixedPreviewAggregatesOnlySelectedSkuDemandAndRequiresFreshQuoteConfirmation() throws Exception {
        var owner = shopper("preview");
        var selections = configuration("preview");
        long cpu = (long) selections.get(0).get("productId");
        var first = data(post(owner, PATH, Map.of("selections", selections)));
        var all = data(post(owner, "/api/cart/items", Map.of("productId", cpu, "quantity", 2, "requestKey", "preview-single")));
        long group = first.path("configurations").get(0).path("id").asLong();
        long item = all.path("items").get(0).path("id").asLong();
        var input = new LinkedHashMap<String, Object>();
        input.put("revision", all.path("revision").asLong());
        input.put("itemIds", List.of(item));
        input.put("configurationIds", List.of(group));
        var inventoryBefore = inventory("preview");
        var response = post(owner, "/api/cart/checkout-preview", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var quote = data(response);
        assertThat(quote.path("status").asText()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(quote.path("total").asText()).isEqualTo("13100.50");
        assertThat(quote.path("items")).hasSize(1);
        assertThat(quote.path("configurations").get(0).path("items")).hasSize(8);
        assertThat(quote.path("skuDemand").findValues("quantity")).extracting(JsonNode::asLong).contains(3L);
        input.put("quoteToken", quote.path("quoteToken").asText());
        assertThat(data(post(owner, "/api/cart/checkout-preview", input)).path("status").asText()).isEqualTo("READY");
        assertThat(inventory("preview")).isEqualTo(inventoryBefore);
        db.update("UPDATE products SET price=1500.01,revision=revision+1 WHERE id=?", cpu);
        var changed = data(post(owner, "/api/cart/checkout-preview", input));
        assertThat(changed.path("status").asText()).isEqualTo("RECONFIRMATION_REQUIRED");
        assertThat(changed.path("total").asText()).isEqualTo("13103.53");
        input.put("quoteToken", changed.path("quoteToken").asText());
        assertThat(data(post(owner, "/api/cart/checkout-preview", input)).path("status").asText()).isEqualTo("READY");
        db.update("UPDATE products SET stock=2 WHERE id=?", cpu);
        assertThat(code(post(owner, "/api/cart/checkout-preview", input))).isEqualTo("CART_SELECTION_INVALID");
        input.put("itemIds", List.of());
        input.remove("quoteToken");
        var subset = post(owner, "/api/cart/checkout-preview", input);
        assertThat(subset.statusCode()).as(subset.body()).isEqualTo(200);
        assertThat(data(subset).path("total").asText()).isEqualTo("10103.51");
        assertThat(data(get(owner, "/api/cart")).path("lineCount").asLong()).isEqualTo(2);
        assertThat(inventory("preview")).allSatisfy(row -> assertThat(row.get("reserved_stock")).isEqualTo(0));
    }

    @Test void failedReplacementAndDuplicateTargetPreserveTheOriginalWholeGroup() throws Exception {
        var owner = shopper("rollback15");
        var selections = configuration("rollback15");
        var first = data(post(owner, PATH, Map.of("selections", selections)));
        long id = first.path("configurations").get(0).path("id").asLong();
        var input = new LinkedHashMap<String, Object>();
        input.put("selections", selections);
        input.put("memoryQuantity", 2);
        input.put("revision", first.path("revision").asLong());
        input.put("editToken", first.path("configurations").get(0).path("editToken").asText());
        String constraint = "fault15_" + prefix;
        // A unique fixture memory count is only introduced by the replacement.
        long memory = (long) selections.get(3).get("productId");
        db.execute("ALTER TABLE cart_configuration_item ADD CONSTRAINT " + constraint + " CHECK (product_id <> " + memory + " OR quantity <> 2)");
        try {
            assertThat(post(owner, PATH + "/" + id, input).statusCode()).isEqualTo(503);
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(first);
        } finally { db.execute("ALTER TABLE cart_configuration_item DROP CHECK " + constraint); }
        var two = data(post(owner, PATH, Map.of("selections", selections, "memoryQuantity", 2)));
        input.put("revision", two.path("revision").asLong());
        assertThat(code(post(owner, PATH + "/" + id, input))).isEqualTo("CONFIGURATION_DUPLICATE");
        assertThat(data(get(owner, "/api/cart"))).isEqualTo(two);
    }

    @Test void previewRejectsForgedPartialForeignAndStaleSelectionsAndRequiresSessionAndCsrf() throws Exception {
        var owner = shopper("security");
        var parts = configuration("security");
        var cart = data(post(owner, PATH, Map.of("selections", parts)));
        long id = cart.path("configurations").get(0).path("id").asLong();
        var input = new LinkedHashMap<String, Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", List.of());
        input.put("configurationIds", List.of(id));
        assertThat(post(browser(), "/api/cart/checkout-preview", input).statusCode()).isEqualTo(401);
        assertThat(rawPost(owner, "/api/cart/checkout-preview", json.writeValueAsString(input), "bad-token").statusCode()).isEqualTo(403);
        for (String field : List.of("user_id", "total", "configurationItems", "quantity")) {
            var forged = new LinkedHashMap<>(input);
            forged.put(field, 1);
            assertThat(code(post(owner, "/api/cart/checkout-preview", forged))).isEqualTo("INVALID_CART_INPUT");
        }
        input.put("configurationIds", List.of(id, id));
        assertThat(code(post(owner, "/api/cart/checkout-preview", input))).isEqualTo("INVALID_CART_INPUT");
        input.put("configurationIds", List.of(id));
        var other = shopper("stranger");
        var otherParts = configuration("stranger");
        post(other, PATH, Map.of("selections", otherParts));
        assertThat(post(other, "/api/cart/checkout-preview", input).statusCode()).isEqualTo(404);
        assertThat(post(other, PATH + "/" + id, Map.of("selections", otherParts, "revision", 1, "editToken", "forged")).statusCode()).isEqualTo(404);
        post(owner, "/api/cart/items", Map.of("productId", parts.get(0).get("productId"), "quantity", 1, "requestKey", "security-modified"));
        assertThat(code(post(owner, "/api/cart/checkout-preview", input))).isEqualTo("CART_CONFLICT");
    }

    @Test void previewWaitsForConcurrentPriceWriteAndRefusesTheOldQuote() throws Exception {
        var owner = shopper("priceRace");
        var parts = configuration("priceRace");
        var cart = data(post(owner, PATH, Map.of("selections", parts)));
        var input = new LinkedHashMap<String, Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", List.of());
        input.put("configurationIds", List.of(cart.path("configurations").get(0).path("id").asLong()));
        input.put("quoteToken", data(post(owner, "/api/cart/checkout-preview", input)).path("quoteToken").asText());
        String token = csrf(owner);
        var executor = Executors.newSingleThreadExecutor();
        try (var catalog = source.getConnection()) {
            catalog.setAutoCommit(false);
            try {
                try (var update = catalog.prepareStatement("UPDATE products SET price=1500.01,revision=revision+1 WHERE id=?")) {
                    update.setLong(1, (long) parts.get(0).get("productId"));
                    update.executeUpdate();
                }
                var started = new CountDownLatch(1);
                var checking = executor.submit(() -> {
                    started.countDown();
                    return rawPost(owner, "/api/cart/checkout-preview", json.writeValueAsString(input), token);
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> checking.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                catalog.commit();
                var response = checking.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(data(response).path("status").asText()).isEqualTo("RECONFIRMATION_REQUIRED");
                assertThat(data(response).path("total").asText()).isEqualTo("10103.51");
            } finally { catalog.rollback(); }
        } finally { executor.shutdownNow(); }
    }

    @Test void simultaneousEditorsCannotSilentlyOverwriteEachOther() throws Exception {
        var owner = shopper("editRace");
        var parts = configuration("editRace");
        var before = data(post(owner, PATH, Map.of("selections", parts)));
        var group = before.path("configurations").get(0);
        String path = PATH + "/" + group.path("id").asLong();
        String token = csrf(owner);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var pending = new ArrayList<Future<HttpResponse<String>>>();
            for (int quantity : List.of(2, 3)) {
                var input = json.writeValueAsString(Map.of("revision", before.path("revision").asLong(), "editToken", group.path("editToken").asText(),
                        "selections", parts, "memoryQuantity", quantity));
                pending.add(executor.submit(() -> { barrier.await(); return rawPost(owner, path, input, token); }));
            }
            var responses = List.of(pending.get(0).get(10, TimeUnit.SECONDS), pending.get(1).get(10, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 409);
            var winner = responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow();
            assertThat(data(get(owner, "/api/cart"))).isEqualTo(data(winner));
            assertThat(data(winner).path("configurations")).hasSize(1);
        } finally { executor.shutdownNow(); }
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
