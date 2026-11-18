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
    void manualAssessmentUsesTheLowestMetricAndCountsBothMemoryModules() throws Exception {
        long cpu = idForSku("INTEL-I5-14600K");
        long gpu = idForSku("NVIDIA-RTX4070SUPER-FE");
        String base = "revision=10&CPU=" + cpu + "&MOTHERBOARD=" + idForSku("ASUS-TUF-B760M-PLUS-WIFI")
                + "&MEMORY=" + ProductBrowserTest.memoryModuleId(app) + "&SSD=" + idForSku("SAMSUNG-MZ-V9P1T0BW")
                + "&CASE=" + idForSku("DEEPCOOL-CC560") + "&PSU=" + idForSku("CORSAIR-CP-9020295")
                + "&COOLER=" + idForSku("DEEPCOOL-AK400");
        String discrete = base + "&GPU=" + gpu;
        assertThat(read(discrete).has("assessment")).isFalse();
        assertThat(read("revision=10&CPU=" + cpu + "&assessPerformance=1").has("assessment")).isFalse();

        var gaming = read(discrete + "&assessPerformance=1").path("assessment");
        assertThat(gaming.path("scenario").asText()).isEqualTo("GAMING");
        assertThat(gaming.path("score").asText()).isEqualTo("70.000000");
        assertThat(gaming.path("bottlenecks").get(0).asText()).isEqualTo("内存容量");
        assertThat(gaming.path("weightedScore").asDouble()).isGreaterThan(70).isLessThanOrEqualTo(100);

        var creation = read(discrete + "&assessPerformance=1&memoryQuantity=2").path("assessment");
        assertThat(creation.path("scenario").asText()).isEqualTo("CREATION");
        assertThat(creation.path("score").asText()).isEqualTo("90.000000");

        var office = read(base + "&assessPerformance=1&integratedGraphicsCpuId=" + cpu).path("assessment");
        assertThat(office.path("scenario").asText()).isEqualTo("OFFICE");
        assertThat(office.path("score").asText()).isEqualTo("40");
        assertThat(ProductBrowserTest.get(app, "/api/diy/quote?" + discrete
                + "&assessPerformance=1&integratedGraphicsCpuId=" + cpu).statusCode()).isEqualTo(400);
        assertThat(ProductBrowserTest.get(app, "/api/diy/quote?" + base
                + "&assessPerformance=1&integratedGraphicsCpuId=" + gpu).statusCode()).isEqualTo(400);

        long withoutIntegratedGraphics = idForSku("INTEL-I5-14600KF");
        var missing = read(base.replace("&CPU=" + cpu + "&", "&CPU=" + withoutIntegratedGraphics + "&")
                + "&assessPerformance=1&integratedGraphicsCpuId=" + withoutIntegratedGraphics).path("assessment");
        assertThat(missing.path("score").isNull()).isTrue();
        assertThat(missing.path("missing").get(0).asText()).isEqualTo("显卡性能");

        var db = app.getBean(JdbcTemplate.class);
        String original = db.queryForObject("SELECT parameters FROM product_specifications WHERE product_id=?", String.class, cpu);
        long incompatibleCpu = idForSku("AMD-R7-7800X3D");
        var incompatible = read(discrete.replace("&CPU=" + cpu + "&", "&CPU=" + incompatibleCpu + "&") + "&assessPerformance=1");
        assertThat(incompatible.path("total").isTextual()).isTrue();
        assertThat(incompatible.has("assessment")).isFalse();
        try {
            var withoutSingleCore = json.createArrayNode();
            for (var parameter : json.readTree(original)) {
                if (!parameter.path("code").asText().equals("geekbench7SingleCore")) withoutSingleCore.add(parameter);
            }
            db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", withoutSingleCore.toString(), cpu);
            var unscored = read(discrete + "&assessPerformance=1").path("assessment");
            assertThat(unscored.path("score").isNull()).isTrue();
            assertThat(unscored.path("scenario").isNull()).isTrue();
            assertThat(unscored.path("missing").get(0).asText()).isEqualTo("CPU 单核");
        } finally { db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", original, cpu); }
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

    private long idForSku(String sku) {
        return app.getBean(JdbcTemplate.class).queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
    }
}
