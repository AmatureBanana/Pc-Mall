package cn.zhuangjimao.catalog;

import cn.zhuangjimao.cart.OrderTimeouts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringBootVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static cn.zhuangjimao.catalog.AcceptanceLoad.TIMED_WAVES;
import static cn.zhuangjimao.catalog.AcceptanceLoad.WARMUP_WAVES;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceAcceptanceIT {
    private static final int USERS = 20;
    private static final int CONFIGURATION_WORKERS = 10;
    private static final Path EVIDENCE = Path.of("target", "performance-acceptance");
    private static final String PASSWORD = "Acceptance24-fixture-password!";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired HikariDataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate transaction;
    @Autowired BenchmarkClock clock;
    @Autowired OrderTimeouts timeouts;
    private PerformanceAcceptanceFixtures fixtures;
    private AcceptanceLoad load;
    private AcceptanceLoad.Session guest;

    @BeforeAll void installCatalogue() throws Exception {
        fixtures = new PerformanceAcceptanceFixtures(db, transaction, json);
        load = new AcceptanceLoad(port, json);
        guest = load.session();
        fixtures.install();
    }

    @AfterAll void cleanUp() {
        try {
            if (fixtures != null) fixtures.cleanUp();
        } finally {
            if (load != null) load.close();
        }
    }

    @Test @Order(1) void catalogueContainsCompleteTraceableProductsAtStandardScale() throws Exception {
        var products = guest.get("/api/products").ok();
        assertThat(products.size()).isBetween(100, 200);
        var categories = new HashSet<String>();
        var identities = new HashSet<String>();
        for (JsonNode product : products) {
            String category = product.path("category").asText();
            categories.add(category);
            assertThat(identities.add(category + "/" + product.path("brand").asText() + "/" + product.path("model").asText()))
                    .as("unique model %s", product.path("sku")).isTrue();
            assertThat(HardwareParameters.httpUrl(product.path("sourceUrl").asText())).isTrue();
            assertThat(product.path("stock").asInt()).isPositive();
            var detail = guest.get("/api/products/" + product.path("id").asLong()).ok();
            var parameters = HardwareParameters.validate(category, detail.path("parameters"), json);
            assertThat(HardwareParameters.missing(category, parameters)).as(product.path("sku").asText()).isEmpty();
            if (Set.of("CPU", "GPU", "MEMORY", "SSD").contains(category)) {
                assertThat(product.path("performanceTags").findValuesAsText("kind"))
                        .as(product.path("sku").asText()).contains("SCORE");
            }
        }
        assertThat(categories).hasSize(HardwareCategory.values().length);
    }

    @Test @Order(2) void warmedBaselineAndConcurrentTransactions() throws Exception {
        try {
            var catalog = guest.get("/api/products").ok();
            assertThat(catalog.size()).isBetween(100, 200);
            writeEnvironment(catalog);
            var shoppers = new ArrayList<AcceptanceLoad.Session>();
            var visitors = new ArrayList<AcceptanceLoad.Session>();
            for (int i = 0; i < USERS; i++) shoppers.add(account("buyer" + i, "USER"));
            for (int i = 0; i < CONFIGURATION_WORKERS; i++) {
                var visitor = load.session();
                visitor.refreshCsrf();
                visitors.add(visitor);
            }
            var admin = account("admin", "ADMIN");
            var storage = fixtures.storageProducts();
            configurationLoad(visitors);
            cartLoad(shoppers, storage);
            orderLoad(shoppers, storage.subList(34, 34 + USERS));
            timed("F07-catalog-page", indexed(USERS, i -> admin.get("/api/admin/products?page=1&size=20")),
                    2_000, false, reply -> assertThat(reply.ok().path("items")).hasSize(20));
            timed("F07-order-page", indexed(USERS, i -> admin.get("/api/admin/orders?page=1&pageSize=20")),
                    2_000, false, reply -> assertThat(reply.ok().path("items")).hasSize(20));
            stockAndOrderRaces(shoppers, admin, storage);
            checkCartAggregation(shoppers.getFirst(), storage.get(55));
            checkCatalogChanges(account("reprice", "USER"), admin);
            load.assertTargets();
        } finally {
            load.writeEvidence(EVIDENCE);
        }
    }

    private void configurationLoad(List<AcceptanceLoad.Session> visitors) throws Exception {
        guest.refreshCsrf();
        var input = new LinkedHashMap<String, Object>(compatibleParts(fixtures.idBySku("SAMSUNG-MZ-V9P1T0BW")));
        input.put("revision", 24);
        timed("F03-check", visitors.stream().<Callable<AcceptanceLoad.Reply>>map(visitor ->
                () -> visitor.post("/api/compatibility/check", input)).toList(), 1_000, true, reply -> {
                    var check = reply.ok();
                    assertThat(check.path("complete").asBoolean()).isTrue();
                    assertThat(check.path("corePassed").asBoolean()).isTrue();
                    assertThat(check.path("revision").asInt()).isEqualTo(24);
                });
        for (String scenario : List.of("OFFICE", "GAMING", "CREATION")) {
            var recommendation = Map.of("scenario", scenario, "budget", "20000.00");
            var expected = guest.post("/api/recommendations", recommendation).ok();
            assertThat(expected.path("status").asText()).as("%s reference plan %s", scenario, expected).isEqualTo("SUCCESS");
            timed("F04-" + scenario, visitors.stream().<Callable<AcceptanceLoad.Reply>>map(visitor ->
                    () -> visitor.post("/api/recommendations", recommendation)).toList(), 3_000, true, reply -> {
                        var result = reply.ok();
                        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
                        assertThat(result.path("compatibility").path("corePassed").asBoolean()).isTrue();
                        assertThat(result.path("items")).isEqualTo(expected.path("items"));
                        assertThat(result.path("scores")).isEqualTo(expected.path("scores"));
                        assertThat(result.path("total")).isEqualTo(expected.path("total"));
                    });
        }
    }

    private Map<String, Long> compatibleParts(long ssd) {
        return Map.of("CPU", fixtures.idBySku("INTEL-I5-14600KF"), "MOTHERBOARD", fixtures.idBySku("ASUS-TUF-B760M-PLUS-WIFI"),
                "GPU", fixtures.idBySku("NVIDIA-RTX4070SUPER-FE"), "MEMORY", fixtures.idBySku("KINGSTON-KF560C36BBE2-16"),
                "SSD", ssd, "PSU", fixtures.idBySku("CORSAIR-CP-9020295"),
                "CASE", fixtures.idBySku("DEEPCOOL-CC560"), "COOLER", fixtures.idBySku("DEEPCOOL-AK400"));
    }

    private void cartLoad(List<AcceptanceLoad.Session> shoppers, List<Long> storage) throws Exception {
        // The benchmark maintains 29 lines before each add, and 30 before reads and edits.
        for (long product : storage.subList(0, 29)) {
            for (var reply : load.wave(indexed(USERS, i -> add(shoppers.get(i), product)))) reply.ok();
        }
        long extra = storage.get(29);
        var adds = new ArrayList<AcceptanceLoad.Reply>();
        var reads = new ArrayList<AcceptanceLoad.Reply>();
        var updates = new ArrayList<AcceptanceLoad.Reply>();
        var deletions = new ArrayList<AcceptanceLoad.Reply>();
        for (int wave = 1; wave <= WARMUP_WAVES + TIMED_WAVES; wave++) {
            String phase = phase(wave);
            var added = load.recordWave("F05-add", phase, wave, indexed(USERS, i -> add(shoppers.get(i), extra)));
            var ids = added.stream().map(reply -> cartItem(reply.ok(), extra)).toList();
            added.forEach(reply -> assertThat(reply.ok().path("items")).hasSize(30));
            var read = load.recordWave("F05-read", phase, wave, indexed(USERS, i -> shoppers.get(i).get("/api/cart")));
            read.forEach(reply -> assertThat(reply.ok().path("items")).hasSize(30));
            var updated = load.recordWave("F05-quantity", phase, wave,
                    indexed(USERS, i -> shoppers.get(i).post(itemPath(ids.get(i)), Map.of("quantity", 2))));
            updated.forEach(reply -> assertThat(item(reply.ok(), extra).path("quantity").asInt()).isEqualTo(2));
            var deleted = load.recordWave("F05-delete", phase, wave,
                    indexed(USERS, i -> shoppers.get(i).delete("/api/cart/items/" + ids.get(i))));
            deleted.forEach(reply -> assertThat(reply.ok().path("items")).hasSize(29));
            if (phase.equals("timed")) {
                adds.addAll(added);
                reads.addAll(read);
                updates.addAll(updated);
                deletions.addAll(deleted);
            }
        }
        load.summarize("F05-add", USERS, adds, 2_000, true);
        load.summarize("F05-read", USERS, reads, 2_000, true);
        load.summarize("F05-quantity", USERS, updates, 2_000, true);
        load.summarize("F05-delete", USERS, deletions, 2_000, true);
    }

    private void orderLoad(List<AcceptanceLoad.Session> shoppers, List<Long> products) throws Exception {
        var orderNumbers = new ArrayList<String>();
        var timings = new ArrayList<AcceptanceLoad.Reply>();
        for (int wave = 1; wave <= WARMUP_WAVES + TIMED_WAVES; wave++) {
            var quotes = load.wave(indexed(USERS, i -> orderInput(shoppers.get(i), products.get(i))));
            var orders = load.recordWave("F06-create", phase(wave), wave,
                    indexed(USERS, i -> shoppers.get(i).post("/api/orders", quotes.get(i))));
            orderNumbers.clear();
            for (var response : orders) {
                var order = response.ok();
                assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
                assertThat(order.path("items")).hasSize(1);
                orderNumbers.add(order.path("orderNumber").asText());
            }
            assertThat(orderNumbers).doesNotHaveDuplicates();
            if (phase(wave).equals("timed")) timings.addAll(orders);
        }
        load.summarize("F06-create", USERS, timings, 3_000, true);
        timed("F06-history", indexed(USERS, i -> shoppers.get(i).get("/api/orders?page=1&pageSize=20")),
                2_000, true, reply -> assertThat(reply.ok().path("items")).hasSize(WARMUP_WAVES + TIMED_WAVES));
        timed("F06-detail", indexed(USERS, i -> shoppers.get(i).get("/api/orders/" + orderNumbers.get(i))),
                2_000, true, reply -> assertThat(reply.ok().path("status").asText()).isEqualTo("PENDING_PAYMENT"));
    }

    private void stockAndOrderRaces(List<AcceptanceLoad.Session> shoppers, AcceptanceLoad.Session admin, List<Long> storage) throws Exception {
        long soldOut = storage.get(30);
        fixtures.resetFixtureStock(soldOut, 1);
        var quotes = load.wave(indexed(USERS, i -> orderInput(shoppers.get(i), soldOut)));
        var results = load.recordWave("race-limited-stock", "race", 1,
                indexed(USERS, i -> shoppers.get(i).post("/api/orders", quotes.get(i))));
        assertThat(verifyPurchases(results, shoppers, soldOut, "CART_SELECTION_INVALID", "STOCK_CONFLICT")).isEqualTo(1);
        assertStock(admin, soldOut, 0, 1);
        removeRejectedLines(shoppers, soldOut);

        long adjusted = storage.get(31);
        fixtures.resetFixtureStock(adjusted, 2);
        var inputs = load.wave(indexed(USERS - 2, i -> orderInput(shoppers.get(i), adjusted)));
        var competing = new ArrayList<>(indexed(USERS - 2, i -> shoppers.get(i).post("/api/orders", inputs.get(i))));
        for (int i = 1; i <= 2; i++) {
            String reason = "Task 24 concurrent restock " + i;
            competing.add(() -> admin.post("/api/admin/products/" + adjusted + "/stock", Map.of("delta", 1, "reason", reason)));
        }
        var changes = load.recordWave("race-order-and-restock", "race", 1, competing);
        changes.subList(USERS - 2, USERS).forEach(AcceptanceLoad.Reply::ok);
        int committed = verifyPurchases(changes.subList(0, USERS - 2), shoppers, adjusted, "PRICE_CONFIRMATION_REQUIRED");
        assertThat(committed).isBetween(0, 1);
        assertStock(admin, adjusted, 4 - committed, committed);
        if (committed == 0) {
            // If restocks invalidate every quote, verify one of the preserved carts can confirm and buy at the new stock.
            var buyer = shoppers.getFirst();
            var refreshed = quote(buyer, buyer.get("/api/cart").ok(), adjusted);
            var purchase = load.recordWave("race-restock-reconfirmed", "correctness", 1,
                    List.of(() -> buyer.post("/api/orders", refreshed))).getFirst();
            assertThat(verifyPurchases(List.of(purchase), shoppers, adjusted)).isEqualTo(1);
            assertStock(admin, adjusted, 3, 1);
        }
        removeRejectedLines(shoppers, adjusted);
        var audits = admin.get("/api/admin/products/" + adjusted + "/audit").ok();
        assertThat(audits).hasSize(2);
        assertThat(audits.findValuesAsText("actorUsername")).containsOnly(fixtures.username("admin"));
        assertThat(audits.findValuesAsText("action")).containsExactly("STOCK", "STOCK");
        assertThat(audits.findValuesAsText("reason")).contains("Task 24 concurrent restock 1", "Task 24 concurrent restock 2");
        for (var audit : audits) {
            var before = audit.path("beforeValue");
            var after = audit.path("afterValue");
            assertThat(after.path("stock").asInt()).isEqualTo(before.path("stock").asInt() + 1);
            assertThat(after.path("revision").asLong()).isEqualTo(before.path("revision").asLong() + 1);
        }

        int winner = IntStream.range(0, results.size()).filter(i -> results.get(i).status() == 200).findFirst().orElseThrow();
        var owner = shoppers.get(winner);
        String number = results.get(winner).ok().path("orderNumber").asText();
        String path = "/api/orders/" + number;
        var payments = load.recordWave("race-repeat-pay", "race", 1, indexed(USERS, i -> owner.post(path + "/pay", Map.of())));
        var paidAt = payments.getFirst().ok().path("paidAt");
        assertThat(paidAt.asText()).isNotBlank();
        payments.forEach(reply -> {
            assertThat(reply.ok().path("status").asText()).isEqualTo("PAID");
            assertThat(reply.ok().path("paidAt")).isEqualTo(paidAt);
        });
        assertStock(admin, soldOut, 0, 1);
        String adminOrder = "/api/admin/orders/" + number;
        var completed = load.recordWave("race-repeat-complete", "race", 1, indexed(USERS, i -> admin.post(adminOrder + "/complete", Map.of())));
        completed.forEach(reply -> assertThat(reply.ok().path("order").path("status").asText()).isEqualTo("COMPLETED"));
        var completionAudit = admin.get(adminOrder + "/audit").ok();
        assertThat(completionAudit).hasSize(1);
        assertThat(completionAudit.get(0).path("beforeValue").path("status").asText()).isEqualTo("PAID");
        assertThat(completionAudit.get(0).path("afterValue").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completionAudit.get(0).path("actorUsername").asText()).isEqualTo(fixtures.username("admin"));
        assertStock(admin, soldOut, 0, 1);

        releaseRace(shoppers.getFirst(), admin, storage.get(32), storage.get(33));
    }

    private void checkCartAggregation(AcceptanceLoad.Session buyer, long product) throws Exception {
        fixtures.resetFixtureStock(product, 1);
        var selections = compatibleParts(product).entrySet().stream()
                .map(part -> Map.of("category", part.getKey(), "productId", part.getValue())).toList();
        var grouped = buyer.post("/api/cart/configurations", Map.of("selections", selections)).ok();
        var groups = grouped.path("configurations");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).path("valid").asBoolean()).isTrue();
        var refusal = load.recordWave("cart-aggregate-stock", "correctness", 1, List.of(() -> add(buyer, product))).getFirst();
        assertThat(refusal.status()).isEqualTo(409);
        assertThat(refusal.result()).isEqualTo("CART_STOCK_LIMIT");
        assertThat(buyer.get("/api/cart").ok()).isEqualTo(grouped);
    }

    private void checkCatalogChanges(AcceptanceLoad.Session buyer, AcceptanceLoad.Session admin) throws Exception {
        var recommendation = Map.of("scenario", "OFFICE", "budget", "20000.00");
        var first = guest.post("/api/recommendations", recommendation).ok();
        assertThat(first.path("status").asText()).isEqualTo("SUCCESS");
        long product = 0;
        for (var part : first.path("items")) {
            if (part.path("category").asText().equals("SSD")) product = part.path("productId").asLong();
        }
        assertThat(fixtures.ownsProduct(product)).as("recommended SSD belongs to the benchmark fixture").isTrue();
        String productPath = "/api/admin/products/" + product;
        var startingStock = admin.get(productPath).ok();
        int startingAudits = admin.get(productPath + "/audit").ok().size();
        int available = startingStock.path("product").path("stock").asInt();
        int reserved = startingStock.path("reservedStock").asInt();
        assertThat(available).isGreaterThanOrEqualTo(2);
        var original = buyer.post("/api/orders", orderInput(buyer, product)).ok();
        String orderPath = "/api/orders/" + original.path("orderNumber").asText();
        var proposal = orderInput(buyer, product);
        var before = admin.get(productPath).ok();
        String newPrice = "20000.01";
        var afterPrice = admin.post(productPath + "/price", Map.of("revision", before.path("revision").asLong(),
                "price", newPrice, "reason", "Task 24 stale quote")).ok();
        // Withdraw an optional performance field to simulate a source that is awaiting re-verification.
        var parameters = json.createArrayNode();
        before.path("parameters").forEach(parameter -> {
            if (!parameter.path("code").asText().equals("sequentialWrite")) parameters.add(parameter);
        });
        assertThat(parameters.size()).isEqualTo(before.path("parameters").size() - 1);
        var edit = json.createObjectNode().put("revision", afterPrice.path("revision").asLong())
                .put("description", before.path("description").asText());
        for (String field : List.of("name", "brand", "model", "summary", "imageUrl", "sourceUrl", "priceNote")) {
            edit.set(field, before.path("product").path(field));
        }
        edit.set("parameters", parameters);
        admin.post(productPath, edit).ok();
        var detail = guest.get("/api/products/" + product).ok();
        assertThat(detail.path("performance").path("state").asText()).isEqualTo("MISSING");
        var current = guest.post("/api/recommendations", recommendation).ok();
        assertThat(current.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item(current, product).isMissingNode()).isTrue();
        var refused = load.recordWave("order-stale-quote", "correctness", 1,
                List.of(() -> buyer.post("/api/orders", proposal))).getFirst();
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.result()).isEqualTo("PRICE_CONFIRMATION_REQUIRED");
        var cart = buyer.get("/api/cart").ok();
        assertThat(item(cart, product).path("price").asText()).isEqualTo(newPrice);
        assertThat(buyer.get(orderPath).ok()).isEqualTo(original);
        assertStock(admin, product, available - 1, reserved + 1);
        var reconfirmed = quote(buyer, cart, product);
        var purchased = load.recordWave("order-current-quote", "correctness", 1,
                List.of(() -> buyer.post("/api/orders", reconfirmed))).getFirst().ok();
        assertThat(purchased.path("total").asText()).isEqualTo(newPrice);
        assertThat(item(purchased, product).path("price").asText()).isEqualTo(newPrice);
        assertThat(buyer.get("/api/cart").ok().path("items")).isEmpty();
        assertStock(admin, product, available - 2, reserved + 2);
        var audit = admin.get(productPath + "/audit").ok();
        assertThat(audit).hasSize(startingAudits + 2);
        assertThat(audit.get(0).path("action").asText()).isEqualTo("EDIT");
        assertThat(audit.get(1).path("action").asText()).isEqualTo("PRICE");
        for (int i = 0; i < 2; i++) {
            assertThat(audit.get(i).path("actorUsername").asText()).isEqualTo(fixtures.username("admin"));
        }
    }

    private void releaseRace(AcceptanceLoad.Session owner, AcceptanceLoad.Session admin, long timeoutSku, long raceSku) throws Exception {
        // All unrelated orders in this run are dated after this isolated clock window.
        Instant previous = clock.now.getAndSet(Instant.parse("1999-01-01T00:00:00Z"));
        try {
            var expiring = owner.post("/api/orders", orderInput(owner, timeoutSku)).ok();
            var racing = owner.post("/api/orders", orderInput(owner, raceSku)).ok();
            String expires = "/api/orders/" + expiring.path("orderNumber").asText();
            String races = "/api/orders/" + racing.path("orderNumber").asText();
            clock.now.set(Instant.parse(expiring.path("paymentDeadline").asText()));
            var refused = load.recordWave("order-expired-payment", "correctness", 1,
                    List.of(() -> owner.post(expires + "/pay", Map.of()))).getFirst();
            assertThat(refused.status()).isEqualTo(409);
            assertThat(refused.result()).isEqualTo("ORDER_PAYMENT_EXPIRED");
            clock.now.set(Instant.parse(racing.path("paymentDeadline").asText()));
            var contenders = new ArrayList<>(indexed(USERS - 1, i -> owner.post(races + "/cancel", Map.of())));
            contenders.add(() -> {
                timeouts.scan();
                return owner.get(races);
            });
            var released = load.recordWave("race-cancel-and-timeout", "race", 1, contenders);
            var terminal = owner.get(races).ok();
            assertThat(terminal.path("status").asText()).isEqualTo("CANCELLED");
            assertThat(terminal.path("cancellationReason").asText()).isIn("TIMEOUT", "USER");
            released.subList(0, USERS - 1).forEach(response -> assertThat(response.ok()).isEqualTo(terminal));
            assertThat(owner.get(expires).ok().path("cancellationReason").asText()).isEqualTo("TIMEOUT");
            timeouts.scan();
            assertStock(admin, timeoutSku, PerformanceAcceptanceFixtures.STOCK, 0);
            assertStock(admin, raceSku, PerformanceAcceptanceFixtures.STOCK, 0);
            var cart = owner.get("/api/cart").ok();
            assertThat(cart.path("items").findValues("productId")).noneMatch(id -> id.asLong() == timeoutSku || id.asLong() == raceSku);
        } finally {
            clock.now.set(previous);
        }
    }

    private int verifyPurchases(List<AcceptanceLoad.Reply> results, List<AcceptanceLoad.Session> shoppers, long product, String... conflicts)
            throws Exception {
        var unique = new HashSet<String>();
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            var cart = shoppers.get(i).get("/api/cart").ok();
            if (result.status() == 200) {
                var order = result.ok();
                assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
                assertThat(unique.add(order.path("orderNumber").asText())).isTrue();
                assertThat(order.path("items")).hasSize(1);
                assertThat(item(order, product).path("quantity").asInt()).isEqualTo(1);
                assertThat(cart.path("items")).hasSize(29);
                assertThat(item(cart, product).isMissingNode()).isTrue();
            } else {
                assertThat(result.status()).as(result.body().toString()).isEqualTo(409);
                assertThat(result.result()).isIn(conflicts);
                assertThat(cart.path("items")).hasSize(30);
                assertThat(item(cart, product).path("quantity").asInt()).isEqualTo(1);
            }
        }
        return unique.size();
    }

    private void removeRejectedLines(List<AcceptanceLoad.Session> shoppers, long product) throws Exception {
        for (var shopper : shoppers) {
            long line = cartItem(shopper.get("/api/cart").ok(), product);
            if (line > 0) shopper.delete("/api/cart/items/" + line).ok();
        }
    }

    private void assertStock(AcceptanceLoad.Session admin, long product, int available, int reserved) throws Exception {
        var details = admin.get("/api/admin/products/" + product).ok();
        assertThat(details.path("product").path("stock").asInt()).isEqualTo(available);
        assertThat(details.path("reservedStock").asInt()).isEqualTo(reserved);
    }

    private AcceptanceLoad.Session account(String suffix, String role) throws Exception {
        String username = fixtures.username(suffix);
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES(?,?,?,?)",
                username, passwords.encode(PASSWORD), username, role);
        var session = load.session();
        session.refreshCsrf();
        session.post("/api/auth/login", Map.of("username", username, "password", PASSWORD)).ok();
        session.refreshCsrf();
        return session;
    }

    private Map<String, Object> orderInput(AcceptanceLoad.Session buyer, long product) throws Exception {
        return quote(buyer, add(buyer, product).ok(), product);
    }

    private Map<String, Object> quote(AcceptanceLoad.Session buyer, JsonNode cart, long product) throws Exception {
        var input = new LinkedHashMap<String, Object>();
        input.put("revision", cart.path("revision").asLong());
        input.put("itemIds", List.of(cartItem(cart, product)));
        input.put("configurationIds", List.of());
        var quote = buyer.post("/api/cart/checkout-preview", input).ok();
        assertThat(quote.path("status").asText()).as(quote.toString()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(quote.path("quoteToken").asText()).matches("[a-f0-9]{64}");
        input.put("quoteToken", quote.path("quoteToken").asText());
        input.put("requestKey", UUID.randomUUID().toString());
        return input;
    }

    private AcceptanceLoad.Reply add(AcceptanceLoad.Session buyer, long product) throws Exception {
        return buyer.post("/api/cart/items", Map.of("productId", product, "quantity", 1, "requestKey", UUID.randomUUID().toString()));
    }

    private static JsonNode item(JsonNode data, long product) {
        for (var item : data.path("items")) if (item.path("productId").asLong() == product) return item;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static long cartItem(JsonNode cart, long product) { return item(cart, product).path("id").asLong(); }

    private static String itemPath(long id) { return "/api/cart/items/" + id + "/quantity"; }

    private void timed(String operation, List<Callable<AcceptanceLoad.Reply>> requests, long target, boolean required,
                       java.util.function.Consumer<AcceptanceLoad.Reply> verify) throws Exception {
        var timings = new ArrayList<AcceptanceLoad.Reply>();
        for (int wave = 1; wave <= WARMUP_WAVES + TIMED_WAVES; wave++) {
            var replies = load.recordWave(operation, phase(wave), wave, requests);
            replies.forEach(verify);
            if (phase(wave).equals("timed")) timings.addAll(replies);
        }
        load.summarize(operation, requests.size(), timings, target, required);
    }

    private static String phase(int wave) { return wave <= WARMUP_WAVES ? "warmup" : "timed"; }

    private void writeEnvironment(JsonNode catalog) throws Exception {
        var counts = new TreeMap<String, Integer>();
        for (var product : catalog) counts.merge(product.path("category").asText(), 1, Integer::sum);
        var runtime = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        var snapshot = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(PerformanceAcceptanceFixtures.DATA.resolve("catalog.json"))));
        var environment = json.createObjectNode()
                .put("startedAt", Instant.now().atZone(ZoneId.of("Asia/Shanghai")).toString())
                .put("os", runtime.getName() + " " + runtime.getVersion() + " " + runtime.getArch())
                .put("logicalProcessors", runtime.getAvailableProcessors())
                .put("physicalMemoryBytes", runtime.getTotalMemorySize())
                .put("java", System.getProperty("java.runtime.version"))
                .put("springBoot", SpringBootVersion.getVersion())
                .put("databasePoolSize", dataSource.getMaximumPoolSize())
                .put("catalogVersion", PerformanceAcceptanceFixtures.VERSION)
                .put("catalogSha256", snapshot)
                .put("productsOnSale", catalog.size())
                .put("warmupWavesPerOperation", WARMUP_WAVES)
                .put("timedWavesPerOperation", TIMED_WAVES);
        environment.set("categories", json.valueToTree(counts));
        environment.set("database", json.valueToTree(db.queryForMap(
                "SELECT DATABASE() AS databaseName, VERSION() AS version, @@innodb_buffer_pool_size AS bufferPoolBytes")));
        Files.createDirectories(EVIDENCE);
        json.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve("environment.json").toFile(), environment);
    }

    @FunctionalInterface
    interface Worker<T> { T run(int worker) throws Exception; }

    private static <T> List<Callable<T>> indexed(int count, Worker<T> request) {
        return IntStream.range(0, count).<Callable<T>>mapToObj(i -> () -> request.run(i)).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary BenchmarkClock benchmarkClock() { return new BenchmarkClock(); }
    }

    static final class BenchmarkClock extends Clock {
        final AtomicReference<Instant> now;
        private final ZoneId zone;

        BenchmarkClock() { this(new AtomicReference<>(Instant.parse("2000-01-01T00:00:00Z")), ZoneOffset.UTC); }

        private BenchmarkClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        @Override public Instant instant() { return now.getAndUpdate(value -> value.plusNanos(1_000)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new BenchmarkClock(now, zone); }
    }
}
