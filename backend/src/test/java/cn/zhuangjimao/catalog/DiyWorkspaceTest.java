package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** F02 quotation boundary: real HTTP and isolated MySQL; no saved guest configuration. */
class DiyWorkspaceTest {
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
    void guestQuotesArbitraryOrderWithOneSalesUnitPerCategoryAndExactMoney() throws Exception {
        var response = ProductBrowserTest.get(app, "/api/diy/quote?revision=7&CPU=1&MEMORY=" + ProductBrowserTest.memoryModuleId(app));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode quote = json.readTree(response.body()).path("data");
        assertThat(quote.path("revision").asInt()).isEqualTo(7);
        assertThat(quote.path("total").asText()).isEqualTo("1948.50");
        assertThat(quote.path("compatibility").asText()).isEqualTo("NOT_CHECKED");
        assertThat(quote.path("items").size()).isEqualTo(2);
        for (JsonNode item : quote.path("items")) {
            assertThat(item.path("state").asText()).isEqualTo("VALID");
            assertThat(item.path("quantity").asInt()).isEqualTo(1);
            assertThat(item.path("product").path("sku").asText()).isNotBlank();
        }
    }

    @Test
    void fourMemoryModulesQuoteFourSingleModulePrices() throws Exception {
        String query = "revision=12&MEMORY=" + ProductBrowserTest.memoryModuleId(app) + "&memoryQuantity=";
        var quote = read(query + "4");
        assertThat(quote.path("total").asText()).isEqualTo("1798.00");
        assertThat(quote.path("items").get(0).path("quantity").asInt()).isEqualTo(4);
        assertThat(ProductBrowserTest.get(app, "/api/diy/quote?" + query + "5").statusCode()).isEqualTo(400);
    }

    @Test
    void rejectsDuplicateCategoriesQuantitiesClientPricesAndInvalidReferences() throws Exception {
        for (String query : new String[]{"", "revision=-1", "revision=1&CPU=1&CPU=2", "revision=1&ALL=1",
                "revision=1&CPU=0", "revision=1&CPU=abc", "revision=1&CPU=9007199254740992",
                "revision=1&quantity=2", "revision=1&price=0", "revision=1&revision=2"}) {
            var response = ProductBrowserTest.get(app, "/api/diy/quote?" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("INVALID_CONFIGURATION");
        }
        var empty = read("revision=8");
        assertThat(empty.path("total").asText()).isEqualTo("0.00");
        assertThat(empty.path("items").size()).isZero();
    }

    @Test
    void missingOrWrongCategorySelectionsRemainVisibleAndPreventAFullQuote() throws Exception {
        var quote = read("revision=9&CPU=7&CASE=9007199254740991&MEMORY=" + ProductBrowserTest.memoryModuleId(app));
        assertThat(quote.path("total").isNull()).isTrue();
        assertThat(quote.path("validSubtotal").asText()).isEqualTo("449.50");
        assertThat(quote.path("items").size()).isEqualTo(3);
        assertThat(quote.path("items").findValuesAsText("state"))
                .containsExactlyInAnyOrder("CATEGORY_MISMATCH", "NOT_FOUND", "VALID");
    }

    @Test
    void freshQuoteReflectsPriceStockAndSaleChangesWithoutCachingOrLosingReferences() throws Exception {
        // Database operations are fixture setup only; assertions use the public quotation API.
        var db = app.getBean(JdbcTemplate.class);
        String sku = "t09_" + UUID.randomUUID();
        db.update("""
            INSERT INTO products(sku,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
            SELECT ?,name,category,brand,model,summary,image_url,100.10,1,status,source_url,price_note,data_version
            FROM products WHERE id=1
            """, sku);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
        try {
            String query = "revision=11&CPU=" + id;
            assertThat(read(query).path("total").asText()).isEqualTo("100.10");
            db.update("UPDATE products SET price=123.45,revision=revision+1 WHERE id=?", id);
            assertThat(read(query).path("total").asText()).isEqualTo("123.45");
            db.update("UPDATE products SET stock=0 WHERE id=?", id);
            var stock = read(query);
            assertThat(stock.path("total").isNull()).isTrue();
            assertThat(stock.path("items").get(0).path("state").asText()).isEqualTo("OUT_OF_STOCK");
            db.update("UPDATE products SET stock=1,status='OFF_SALE' WHERE id=?", id);
            var offSale = read(query);
            assertThat(offSale.path("items").get(0).path("productId").asLong()).isEqualTo(id);
            assertThat(offSale.path("items").get(0).path("state").asText()).isEqualTo("OFF_SALE");
            assertThat(offSale.path("total").isNull()).isTrue();
            assertThat(offSale.path("validSubtotal").asText()).isEqualTo("0.00");
        } finally { db.update("DELETE FROM products WHERE id=? AND sku=?", id, sku); }
    }

    private JsonNode read(String query) throws Exception {
        var response = ProductBrowserTest.get(app, "/api/diy/quote?" + query);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return json.readTree(response.body()).path("data");
    }
}
