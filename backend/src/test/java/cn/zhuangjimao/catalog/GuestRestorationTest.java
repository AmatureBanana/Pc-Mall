package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F01-11 / F02-16 restoration boundary: a guest workspace the client already holds can be revalidated
 * through the existing quote and compatibility entrances without any persisted purchase side effect.
 */
class GuestRestorationTest {
    private static ConfigurableApplicationContext app;
    private static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void start() {
        app = ProductBrowserTest.start(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
    }

    @AfterAll
    static void stop() { if (app != null) app.close(); }

    @Test
    void restoringAReferredWorkspaceReadsCurrentCatalogAndCreatesNoCartReservationOrAudit() throws Exception {
        JdbcTemplate db = app.getBean(JdbcTemplate.class);
        long product = cloneProduct(db);
        try {
            // A guest holds only product references; restoration must read the current server values.
            String quoteQuery = "/api/diy/quote?revision=7&CPU=%d&MEMORY=%d".formatted(product, ProductBrowserTest.memoryModuleId(app));
            JsonNode before = read(ProductBrowserTest.get(app, quoteQuery));
            assertThat(before.path("total").asText()).isEqualTo("1948.50");
            assertThat(before.path("compatibility").asText()).isEqualTo("NOT_CHECKED");

            List<String> tablesBefore = tables(db);
            Map<Long, Integer> reservationsBefore = reservations(db);
            String auditBefore = auditSnapshot(db);
            long accountsBefore = db.queryForObject("SELECT COUNT(*) FROM account", Long.class);
            JsonNode partial = json.readTree(check("""
                    {"revision":7,"CPU":%d}""".formatted(product))).path("data");
            assertThat(partial.path("missing")).isNotEmpty();
            assertThat(partial.path("corePassed").asBoolean()).isFalse();

            // Price and parameter changes are re-read rather than served from any cached conclusion.
            db.update("UPDATE products SET price=1199.00,revision=revision+1 WHERE id=?", product);
            setParameter(db, product, "socket", json.getNodeFactory().textNode("AM5"));
            JsonNode after = read(ProductBrowserTest.get(app, quoteQuery));
            assertThat(after.path("total").asText()).isEqualTo("1648.50");
            JsonNode changed = json.readTree(check("""
                    {"revision":7,"CPU":%d,"MOTHERBOARD":3}""".formatted(product))).path("data");
            assertThat(rule(changed, "C01").path("state").asText()).isEqualTo("FAIL");

            assertThat(reservations(db)).as("restoration reserves no stock").isEqualTo(reservationsBefore);
            assertThat(auditSnapshot(db)).as("restoration writes no audit record").isEqualTo(auditBefore);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM account", Long.class)).as("restoration creates no account")
                    .isEqualTo(accountsBefore);
            assertThat(tables(db)).as("restoration creates no cart or order storage").isEqualTo(tablesBefore);
        } finally {
            db.update("DELETE FROM product_specifications WHERE product_id=?", product);
            db.update("DELETE FROM products WHERE id=?", product);
        }
    }

    @Test
    void repeatingTheSameRestorationReadIsStableAndLeavesNoServerSideWorkspace() throws Exception {
        JdbcTemplate db = app.getBean(JdbcTemplate.class);
        long accountsBefore = db.queryForObject("SELECT COUNT(*) FROM account", Long.class);
        List<String> tablesBefore = tables(db);
        Map<Long, Integer> reservationsBefore = reservations(db);

        String query = "/api/diy/quote?revision=9&CPU=1&MEMORY=" + ProductBrowserTest.memoryModuleId(app);
        String first = read(ProductBrowserTest.get(app, query)).toString();
        String repeated = read(ProductBrowserTest.get(app, query)).toString();

        assertThat(repeated).as("no saved multi-plan or guest workspace changes the answer").isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isEqualTo(accountsBefore);
        assertThat(tables(db)).isEqualTo(tablesBefore);
        assertThat(reservations(db)).isEqualTo(reservationsBefore);
    }

    @Test
    void aChangedOrInvalidReferenceStaysVisibleSoTheUserCanReplaceIt() throws Exception {
        JdbcTemplate db = app.getBean(JdbcTemplate.class);
        long product = cloneProduct(db);
        try {
            db.update("UPDATE products SET status='OFF_SALE' WHERE id=?", product);
            JsonNode offSale = read(ProductBrowserTest.get(app, "/api/diy/quote?revision=3&CPU=" + product));
            assertThat(offSale.path("items").get(0).path("state").asText()).isEqualTo("OFF_SALE");
            assertThat(offSale.path("items").get(0).path("productId").asLong()).isEqualTo(product);
            assertThat(offSale.path("total").isNull()).isTrue();

            db.update("UPDATE products SET status='ON_SALE',stock=0 WHERE id=?", product);
            JsonNode noStock = read(ProductBrowserTest.get(app, "/api/diy/quote?revision=4&CPU=" + product));
            assertThat(noStock.path("items").get(0).path("state").asText()).isEqualTo("OUT_OF_STOCK");
        } finally {
            db.update("DELETE FROM product_specifications WHERE product_id=?", product);
            db.update("DELETE FROM products WHERE id=?", product);
        }

        JsonNode absent = read(ProductBrowserTest.get(app, "/api/diy/quote?revision=5&CPU=9007199254740991"));
        assertThat(absent.path("items").get(0).path("state").asText()).isEqualTo("NOT_FOUND");
        assertThat(absent.path("items").get(0).path("product").isNull()).isTrue();
        assertThat(absent.path("total").isNull()).isTrue();
    }

    private static JsonNode read(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return json.readTree(response.body()).path("data");
    }

    private static List<String> tables(JdbcTemplate db) {
        return db.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name
                """, String.class);
    }

    private static Map<Long, Integer> reservations(JdbcTemplate db) {
        return db.query("SELECT p.id, p.reserved_stock FROM products p WHERE p.reserved_stock <> 0", result -> {
            var counts = new java.util.TreeMap<Long, Integer>();
            while (result.next()) counts.put(result.getLong("id"), result.getInt("reserved_stock"));
            return counts;
        });
    }

    private static String auditSnapshot(JdbcTemplate db) {
        return db.queryForObject("SELECT COUNT(*) FROM admin_audit", Long.class) + "/"
                + db.queryForObject("SELECT COALESCE(MAX(id),0) FROM admin_audit", Long.class);
    }

    private void setParameter(JdbcTemplate db, long product, String code, JsonNode value) throws Exception {
        String stored = db.queryForObject("SELECT parameters FROM product_specifications WHERE product_id=?", String.class, product);
        JsonNode parameters = json.readTree(stored);
        int index = -1;
        for (int candidate = 0; candidate < parameters.size(); candidate++) {
            if (parameters.get(candidate).path("code").asText().equals(code)) { index = candidate; break; }
        }
        assertThat(index).as("fixture product has " + code).isNotNegative();
        db.update("UPDATE product_specifications SET parameters=JSON_SET(parameters, CONCAT('$[', ?, '].value'), ?)"
                + " WHERE product_id=?", index, value.asText(), product);
    }

    private static long cloneProduct(JdbcTemplate db) {
        String sku = "t12_" + UUID.randomUUID();
        db.update("""
                INSERT INTO products(sku,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
                SELECT ?,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version
                FROM products WHERE id=1
                """, sku);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
        db.update("""
                INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on)
                SELECT ?,description,parameters,data_version,verified_on FROM product_specifications WHERE product_id=1
                """, id);
        return id;
    }

    private static JsonNode rule(JsonNode data, String code) {
        for (JsonNode check : data.path("checks")) if (check.path("rule").asText().equals(code)) return check;
        throw new AssertionError("missing rule " + code);
    }

    /** Anonymous POST that still honours the project's CSRF policy, as the browser does. */
    private String check(String body) throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        var csrf = browser.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode token = json.readTree(csrf.body()).path("data");
        return browser.send(HttpRequest.newBuilder(uri("/api/compatibility/check"))
                .header("Content-Type", "application/json")
                .header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private URI uri(String path) {
        int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) app).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
