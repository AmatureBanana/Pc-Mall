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

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 13 seam: the public cart HTTP interface over the real isolated MySQL test schema.
 * Every assertion goes through the interface a browser would use; the products table is only read
 * afterwards to prove the cart neither reserved nor decremented stock. Each test owns its account,
 * so one test's lines can never leak into another's assertions.
 */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleItemCartTest {
    private static final String PASSWORD = "CartPassword123!";

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    final ObjectMapper json = new ObjectMapper();
    /** Short enough that the derived usernames still fit the 4..20 character account rule. */
    final String prefix = "t13" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);

    @AfterAll void cleanup() {
        var ids = db.queryForList("SELECT id FROM account WHERE username LIKE ?", Long.class, prefix + "%");
        if (!ids.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
            db.update("DELETE FROM cart_request WHERE account_id IN (" + marks + ")", ids.toArray());
            db.update("DELETE g FROM cart_configuration g JOIN shopping_cart c ON c.id=g.cart_id WHERE c.account_id IN (" + marks + ")", ids.toArray());
            db.update("DELETE i FROM shopping_cart_item i JOIN shopping_cart c ON c.id=i.cart_id WHERE c.account_id IN (" + marks + ")", ids.toArray());
            db.update("DELETE FROM shopping_cart WHERE account_id IN (" + marks + ")", ids.toArray());
        }
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username LIKE ?", prefix + "%");
    }

    // --- ownership ---------------------------------------------------------------------------

    @Test void configurationIsValidatedDeduplicatedAndRemovedAsAGroup() throws Exception {
        var owner = shopper("build");
        var selections = new ArrayList<Map<String, Object>>();
        long memory = db.queryForObject("SELECT id FROM products WHERE sku='KINGSTON-KF560C36BBE2-16'", Long.class);
        Map.of("CPU", 1L, "MOTHERBOARD", 3L, "GPU", 5L, "MEMORY", memory,
                "COOLER", 8L, "SSD", 9L, "PSU", 11L, "CASE", 12L)
                .forEach((category, id) -> selections.add(Map.of("category", category, "productId", id)));
        String path = "/api/cart/configurations";
        var request = Map.of("selections", selections);
        assertThat(post(browser(), path, request).statusCode()).isEqualTo(401);
        assertThat(outcome(post(owner, path, Map.of("selections", selections, "corePassed", true))))
                .isEqualTo("INVALID_CART_INPUT");
        var incompatible = selections.stream().map(part -> part.get("category").equals("CPU")
                ? Map.<String, Object>of("category", "CPU", "productId", 2L) : part).toList();
        assertThat(outcome(post(owner, path, Map.of("selections", incompatible))))
                .isEqualTo("CONFIGURATION_NOT_COMPATIBLE");
        assertThat(data(get(owner, "/api/cart")).path("configurations")).isEmpty();

        int stock = stockOf(1);
        int reserved = reservedOf(1);
        var added = post(owner, path, request);
        assertThat(added.statusCode()).isEqualTo(200);
        var groups = data(added).path("configurations");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).path("items")).hasSize(8);
        assertThat(data(added).path("items")).isEmpty();
        Collections.reverse(selections);
        var repeated = post(owner, path, request);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(data(repeated)).isEqualTo(data(added));
        var fourModules = Map.of("selections", selections, "memoryQuantity", 4);
        var expanded = post(owner, path, fourModules);
        assertThat(expanded.statusCode()).isEqualTo(200);
        var expandedGroups = data(expanded).path("configurations");
        assertThat(expandedGroups).hasSize(2);
        var secondGroup = expandedGroups.get(1);
        assertThat(secondGroup.path("amount").asText()).isEqualTo("11451.00");
        assertThat(secondGroup.path("items").findValuesAsText("quantity")).contains("4");
        assertThat(data(post(owner, path, fourModules))).isEqualTo(data(expanded));
        assertThat(outcome(post(owner, "/api/cart/items", add(memory, stockOf(memory) - 4, key())))).isEqualTo("CART_STOCK_LIMIT");
        assertThat(delete(owner, path + "/" + secondGroup.path("id").asLong()).statusCode()).isEqualTo(200);
        assertThat(outcome(post(owner, "/api/cart/items", add(1, stock, key())))).isEqualTo("CART_STOCK_LIMIT");
        assertThat(outcome(post(owner, "/api/cart/items", add(1, 1, key())))).isEqualTo("OK");
        assertThat(stockOf(1)).isEqualTo(stock);
        assertThat(reservedOf(1)).isEqualTo(reserved);

        String groupPath = path + "/" + groups.get(0).path("id").asLong();
        assertThat(delete(shopper("other"), groupPath).statusCode()).isEqualTo(404);
        var removed = delete(owner, groupPath);
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(data(removed).path("configurations")).isEmpty();
        assertThat(data(removed).path("items")).hasSize(1);
    }

    @Test void guestAndForeignOwnerCannotReadOrChangeThisCart() throws Exception {
        long product = product("owner", 10, "199.90");
        var guest = browser();
        assertThat(post(guest, "/api/cart/items", add(product, 1, key())).statusCode()).isEqualTo(401);
        assertThat(get(guest, "/api/cart").statusCode()).isEqualTo(401);
        // Refused before the service ran: no cart row exists for either account yet.
        assertThat(data(get(shopper("o"), "/api/cart")).path("items")).isEmpty();
        assertThat(data(get(shopper("x"), "/api/cart")).path("items")).isEmpty();

        var owner = shopper("o");
        var other = shopper("x");
        assertThat(post(owner, "/api/cart/items", add(product, 2, key())).statusCode()).isEqualTo(200);
        long itemId = data(get(owner, "/api/cart")).path("items").get(0).path("id").asLong();

        // Another owner sees an empty cart and cannot reach this line by its id.
        assertThat(data(get(other, "/api/cart")).path("items")).isEmpty();
        assertThat(outcome(post(other, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 5))))
                .isEqualTo("CART_ITEM_NOT_FOUND");
        assertThat(outcome(delete(other, "/api/cart/items/" + itemId))).isEqualTo("CART_ITEM_NOT_FOUND");
        assertThat(data(get(owner, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        // A forged owner field is refused outright instead of being silently ignored.
        Map<String, Object> forged = new LinkedHashMap<>(add(product, 1, key()));
        forged.put("accountId", 1);
        assertThat(outcome(post(owner, "/api/cart/items", forged))).isEqualTo("INVALID_CART_INPUT");
        Map<String, Object> userId = new LinkedHashMap<>(add(product, 1, key()));
        userId.put("user_id", 1);
        assertThat(outcome(post(owner, "/api/cart/items", userId))).isEqualTo("INVALID_CART_INPUT");
        assertThat(data(get(owner, "/api/cart")).path("items")).hasSize(1);
    }

    // --- merging, quantity and money ---------------------------------------------------------

    @Test void sameSkuMergesIntoOneLineWithServerSideTotal() throws Exception {
        long product = product("merge", 10, "199.90");
        var owner = shopper("m");
        var cart = data(post(owner, "/api/cart/items", add(product, 2, key())));
        assertThat(cart.path("items")).hasSize(1);
        assertThat(cart.path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(cart.path("items").get(0).path("price").asText()).isEqualTo("199.90");
        assertThat(cart.path("items").get(0).path("amount").asText()).isEqualTo("399.80");
        assertThat(cart.path("total").asText()).isEqualTo("399.80");

        var merged = data(post(owner, "/api/cart/items", add(product, 3, key())));
        assertThat(merged.path("items")).hasSize(1);
        assertThat(merged.path("items").get(0).path("quantity").asInt()).isEqualTo(5);
        assertThat(merged.path("total").asText()).isEqualTo("999.50");
        // The cart is a quotation: reading and merging must not touch sellable or reserved stock.
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(reservedOf(product)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservation_items i JOIN products p ON p.id=i.product_id "
                + "WHERE p.sku LIKE ?", Integer.class, prefix + "%")).isZero();
    }

    @Test void noCartOperationReservesOrDecrementsStock() throws Exception {
        long product = product("noReserve", 6, "45.00");
        var owner = shopper("n");
        var before = stockOf(product);
        long itemId = data(post(owner, "/api/cart/items", add(product, 2, key()))).path("items").get(0).path("id").asLong();
        data(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 5)));
        data(get(owner, "/api/cart"));
        data(get(owner, "/api/cart"));
        data(delete(owner, "/api/cart/items/" + itemId));
        // Read, add, re-quantify and remove are all side-effect free on available and reserved stock.
        assertThat(stockOf(product)).isEqualTo(before);
        assertThat(reservedOf(product)).isZero();
        // Scoped to this fixture: another test's reservation must not influence the assertion.
        assertThat(reservationItemsForProduct(product)).isZero();
    }

    @Test void quantityBoundsAndStockLimitKeepTheOriginalLine() throws Exception {
        long product = product("bounds", 500, "199.90");
        var owner = shopper("b");
        long itemId = data(post(owner, "/api/cart/items", add(product, 60, key()))).path("items").get(0).path("id").asLong();
        // 60 + 40 would pass 99: refused, and the existing line keeps its quantity.
        assertThat(outcome(post(owner, "/api/cart/items", add(product, 40, key())))).isEqualTo("CART_QUANTITY_LIMIT");
        assertThat(data(get(owner, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(60);

        // 1..99 is the accepted range; 0 and 100 are rejected as input, not silently clamped.
        assertThat(outcome(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 0)))).isEqualTo("INVALID_CART_INPUT");
        assertThat(outcome(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 100)))).isEqualTo("INVALID_CART_INPUT");
        assertThat(data(get(owner, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(60);
        assertThat(data(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 1))).path("total").asText())
                .isEqualTo("199.90");
        assertThat(delete(owner, "/api/cart/items/" + itemId).statusCode()).isEqualTo(200);
        assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
    }

    @Test void ninetyNineIsAcceptedAndOneMoreIsRefused() throws Exception {
        long deep = product("deep", 500, "12.30");
        var owner = shopper("d");
        var added = data(post(owner, "/api/cart/items", add(deep, 99, key())));
        assertThat(added.path("items").get(0).path("quantity").asInt()).isEqualTo(99);
        assertThat(added.path("total").asText()).isEqualTo("1217.70");
        long itemId = added.path("items").get(0).path("id").asLong();
        // 99 is the bound itself, and re-submitting it changes nothing.
        assertThat(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 99)).statusCode()).isEqualTo(200);
        assertThat(outcome(post(owner, "/api/cart/items", add(deep, 1, key())))).isEqualTo("CART_QUANTITY_LIMIT");
        assertThat(data(get(owner, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(99);

        // The current sellable quantity also caps a write, independently of the 99 bound.
        db.update("UPDATE products SET stock=4 WHERE id=?", deep);
        assertThat(outcome(post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 5)))).isEqualTo("CART_STOCK_LIMIT");
        var limited = data(get(owner, "/api/cart"));
        assertThat(limited.path("items").get(0).path("state").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(limited.path("items").get(0).path("quantity").asInt()).isEqualTo(99);
        assertThat(limited.path("items").get(0).path("amount").asText()).isEqualTo("1217.70");
        assertThat(limited.path("validTotal").asText()).isEqualTo("0.00");
    }

    // --- retries and concurrency -------------------------------------------------------------

    @Test void retryKeyReplaysFirstAnswerAndReuseWithNewContentIsRefused() throws Exception {
        long product = product("retry", 10, "199.90");
        var owner = shopper("r");
        String key = key();
        assertThat(post(owner, "/api/cart/items", add(product, 4, key)).statusCode()).isEqualTo(200);
        var replayed = post(owner, "/api/cart/items", add(product, 4, key));
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(data(replayed).path("items").get(0).path("quantity").asInt()).isEqualTo(4);
        // Same key, different content: refused instead of silently adding another unit.
        assertThat(outcome(post(owner, "/api/cart/items", add(product, 5, key)))).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        // Same content under a fresh key is a genuine second addition and must merge.
        assertThat(data(post(owner, "/api/cart/items", add(product, 1, key()))).path("items").get(0).path("quantity").asInt())
                .isEqualTo(5);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test void retryKeyIsScopedToItsOwner() throws Exception {
        long product = product("keyOwner", 10, "10.00");
        var first = shopper("ka");
        var second = shopper("kb");
        String key = key();
        assertThat(data(post(first, "/api/cart/items", add(product, 2, key))).path("total").asText()).isEqualTo("20.00");
        // The same literal key from another account is a different request, not a replay.
        assertThat(data(post(second, "/api/cart/items", add(product, 3, key))).path("total").asText()).isEqualTo("30.00");
        assertThat(data(get(first, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(data(get(second, "/api/cart")).path("items").get(0).path("quantity").asInt()).isEqualTo(3);
    }

    @Test void concurrentAddsAccumulateWithoutLosingQuantity() throws Exception {
        long target = product("concurrent", 40, "50.00");
        var owner = shopper("c");
        // The token is fetched once: racing CSRF reads would rotate the session and hide the real race.
        String token = csrf(owner);
        var executor = Executors.newFixedThreadPool(4);
        var start = new CyclicBarrier(4);
        try {
            var tasks = new ArrayList<Callable<String>>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> {
                    start.await();
                    var probe = postWith(owner, "/api/cart/items", add(target, 1, key()), token);
                    return probe.statusCode() + " " + probe.body();
                });
            }
            for (var result : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                assertThat(result.get()).as("concurrent add").startsWith("200 ");
            }
        } finally { executor.shutdownNow(); }
        // Four independent single-unit additions under one cart row lock: exactly four, none lost.
        assertThat(quantityOf(owner, target)).isEqualTo(4);
        assertThat(stockOf(target)).isEqualTo(40);
        assertThat(reservedOf(target)).isZero();
    }

    @Test void replayedRequestDuringConcurrencyAddsExactlyOnce() throws Exception {
        long target = product("retryConcurrent", 40, "20.00");
        var owner = shopper("cr");
        String key = key();
        String token = csrf(owner);
        var executor = Executors.newFixedThreadPool(3);
        var start = new CyclicBarrier(3);
        try {
            var tasks = new ArrayList<Callable<Integer>>();
            for (int index = 0; index < 3; index++) {
                tasks.add(() -> {
                    start.await();
                    return postWith(owner, "/api/cart/items", add(target, 2, key), token).statusCode();
                });
            }
            for (var result : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) assertThat(result.get()).isEqualTo(200);
        } finally { executor.shutdownNow(); }
        assertThat(quantityOf(owner, target)).isEqualTo(2);
        assertThat(stockOf(target)).isEqualTo(40);
    }

    // --- persistence and invalid lines -------------------------------------------------------

    @Test void cartSurvivesNewSessionAndKeepsInvalidLinesLabelled() throws Exception {
        long gone = product("gone", 5, "88.00");
        var owner = shopper("p");
        var added = data(post(owner, "/api/cart/items", add(gone, 3, key())));
        long itemId = added.path("items").get(0).path("id").asLong();
        assertThat(added.path("items").get(0).path("state").asText()).isEqualTo("VALID");

        // Signing out and in again reads the same persisted cart.
        assertThat(post(owner, "/api/auth/logout", Map.of()).statusCode()).isEqualTo(200);
        var fresh = shopper("p");
        var restored = data(get(fresh, "/api/cart"));
        assertThat(restored.path("items")).hasSize(1);
        assertThat(restored.path("items").get(0).path("quantity").asInt()).isEqualTo(3);

        db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", gone);
        var offSale = data(get(fresh, "/api/cart"));
        assertThat(offSale.path("items")).hasSize(1);
        assertThat(offSale.path("items").get(0).path("state").asText()).isEqualTo("OFF_SALE");
        assertThat(offSale.path("items").get(0).path("quantity").asInt()).isEqualTo(3);
        assertThat(offSale.path("validTotal").asText()).isEqualTo("0.00");
        // An invalid line is retained but cannot be re-quantified in place.
        // An off-sale line is retained but cannot be re-quantified in place.
        assertThat(outcome(post(fresh, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 1))))
                .isEqualTo("PRODUCT_NOT_SELECTABLE");

        db.update("UPDATE products SET status='ON_SALE', stock=2 WHERE id=?", gone);
        var insufficient = data(get(fresh, "/api/cart"));
        assertThat(insufficient.path("items").get(0).path("state").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(insufficient.path("items").get(0).path("amount").asText()).isEqualTo("264.00");
        assertThat(insufficient.path("validTotal").asText()).isEqualTo("0.00");
        assertThat(delete(fresh, "/api/cart/items/" + itemId).statusCode()).isEqualTo(200);
        assertThat(data(get(fresh, "/api/cart")).path("items")).isEmpty();
    }

    @Test void outOfStockLineCannotChangeQuantityAndKeepsItsRevision() throws Exception {
        long product = product("quantityOut", 5, "88.00");
        var owner = shopper("qo");
        var addedResponse = post(owner, "/api/cart/items", add(product, 3, key()));
        assertThat(addedResponse.statusCode()).isEqualTo(200);
        var added = data(addedResponse);
        long itemId = added.path("items").get(0).path("id").asLong();
        long revision = added.path("revision").asLong();

        db.update("UPDATE products SET stock=0 WHERE id=?", product);
        var refused = post(owner, "/api/cart/items/" + itemId + "/quantity", Map.of("quantity", 1));
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(errorCode(refused)).isEqualTo("PRODUCT_NOT_SELECTABLE");

        var retainedResponse = get(owner, "/api/cart");
        assertThat(retainedResponse.statusCode()).isEqualTo(200);
        var retained = data(retainedResponse);
        assertThat(retained.path("revision").asLong()).isEqualTo(revision);
        assertThat(retained.path("items")).hasSize(1);
        var line = retained.path("items").get(0);
        assertThat(line.path("id").asLong()).isEqualTo(itemId);
        assertThat(line.path("quantity").asInt()).isEqualTo(3);
        assertThat(line.path("state").asText()).isEqualTo("OUT_OF_STOCK");
    }

    @Test void priceChangeReachesNextReadWithoutTouchingStoredLine() throws Exception {
        long product = product("price", 10, "100.00");
        var owner = shopper("pr");
        assertThat(data(post(owner, "/api/cart/items", add(product, 2, key()))).path("total").asText()).isEqualTo("200.00");
        db.update("UPDATE products SET price=130.50, revision=revision+1 WHERE id=?", product);
        var current = data(get(owner, "/api/cart"));
        // The stored line is a reference; the amount is always recomputed from the current price.
        assertThat(current.path("items").get(0).path("price").asText()).isEqualTo("130.50");
        assertThat(current.path("items").get(0).path("amount").asText()).isEqualTo("261.00");
        assertThat(current.path("total").asText()).isEqualTo("261.00");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM shopping_cart_item WHERE product_id=?", Integer.class, product))
                .isEqualTo(1);
    }

    // --- input boundaries --------------------------------------------------------------------

    @Test void missingOffSaleAndOutOfStockProductsCannotBeAdded() throws Exception {
        long product = product("main", 10, "199.90");
        var owner = shopper("i");
        assertThat(outcome(post(owner, "/api/cart/items", add(9_007_199_254_740_991L, 1, key())))).isEqualTo("PRODUCT_NOT_FOUND");
        long offSale = product("offSale", 3, "30.00");
        db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", offSale);
        // Listed but not sellable: the contract names this PRODUCT_NOT_SELECTABLE, not a stock rule.
        assertThat(outcome(post(owner, "/api/cart/items", add(offSale, 1, key())))).isEqualTo("PRODUCT_NOT_SELECTABLE");
        long out = product("outOfStock", 0, "30.00");
        // A zero-stock product is equally not selectable rather than a quantity/stock-limit refusal.
        assertThat(outcome(post(owner, "/api/cart/items", add(out, 1, key())))).isEqualTo("PRODUCT_NOT_SELECTABLE");
        for (Object requestKey : Arrays.asList("", "short", "bad key!", "x".repeat(65), 42, null)) {
            assertThat(post(owner, "/api/cart/items", add(product, 1, requestKey)).statusCode())
                    .as("requestKey %s", requestKey).isEqualTo(400);
        }
        for (Object quantity : Arrays.asList(0, -1, 100, 1.5, "2", null)) {
            assertThat(post(owner, "/api/cart/items", add(product, quantity, key())).statusCode())
                    .as("quantity %s", quantity).isEqualTo(400);
        }
        assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
        assertThat(stockOf(offSale)).isEqualTo(3);
    }

    @Test void csrfIsRequiredForEveryCartWrite() throws Exception {
        long product = product("csrf", 10, "10.00");
        var owner = shopper("cs");
        var noCsrf = owner.send(HttpRequest.newBuilder(URI.create(base() + "/api/cart/items"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(add(product, 1, key())))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(errorCode(noCsrf)).isEqualTo("CSRF_INVALID");
        assertThat(data(get(owner, "/api/cart")).path("items")).isEmpty();
    }

    // --- fixture and HTTP helpers ------------------------------------------------------------

    private String base() { return "http://127.0.0.1:" + port; }

    /** Each test owns a distinct account, so persisted lines never leak between assertions. */
    private HttpClient shopper(String suffix) throws Exception {
        String username = prefix + suffix;
        db.update("INSERT IGNORE INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'USER')",
                username, passwords.encode(PASSWORD), username);
        var client = browser();
        assertThat(post(client, "/api/auth/login", Map.of("username", username, "password", PASSWORD)).statusCode())
                .as("sign in %s", username).isEqualTo(200);
        return client;
    }

    /** Test fixture only: the cart under test never depends on the administration API. */
    private long product(String suffix, int stock, String price) {
        db.update("INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version) "
                        + "VALUES(?, 'PSU', ?, '测试品牌', ?, ?, '/images/psu.svg', ?, ?, 'ON_SALE', 'https://example.com/psu', '测试夹具', 'test')",
                prefix + suffix, "购物车夹具 " + prefix + suffix, "Cart-" + suffix, "任务13夹具", new BigDecimal(price), stock);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, prefix + suffix);
        db.update("INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on) "
                + "VALUES(?,'夹具','[]','test',CURRENT_DATE)", id);
        return id;
    }

    private int stockOf(long id) { return db.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id); }
    private int reservedOf(long id) { return db.queryForObject("SELECT reserved_stock FROM products WHERE id=?", Integer.class, id); }

    /** Reservations belonging to this fixture only, so a shared table is never asserted globally. */
    private int reservationItemsForProduct(long id) {
        return db.queryForObject("SELECT COUNT(*) FROM stock_reservation_items WHERE product_id=?", Integer.class, id);
    }

    /** The structured error code, so a right status with the wrong reason cannot pass unnoticed. */
    private String errorCode(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("code").asText();
    }

    /** The rule's own code when the request was refused, or "OK" when it succeeded. */
    private String outcome(HttpResponse<String> response) throws Exception {
        return response.statusCode() == 200 ? "OK" : errorCode(response);
    }

    private int quantityOf(HttpClient client, long productId) throws Exception {
        for (var item : data(get(client, "/api/cart")).path("items")) {
            if (item.path("productId").asLong() == productId) return item.path("quantity").asInt();
        }
        return 0;
    }

    private static String key() { return "req-" + UUID.randomUUID().toString().substring(0, 12); }

    private static Map<String, Object> add(long productId, Object quantity, Object requestKey) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("productId", productId);
        input.put("quantity", quantity);
        input.put("requestKey", requestKey);
        return input;
    }

    private HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(HttpClient client, String path) throws Exception {
        var csrf = data(get(client, "/api/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).header("X-CSRF-TOKEN", csrf.path("token").asText())
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, Object input) throws Exception {
        return postWith(client, path, input, csrf(client));
    }

    /** Lets a coordinated burst reuse one token instead of racing the session-rotating CSRF read. */
    private HttpResponse<String> postWith(HttpClient client, String path, Object input, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrf(HttpClient client) throws Exception {
        return data(get(client, "/api/auth/csrf")).path("token").asText();
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("data");
    }
}
