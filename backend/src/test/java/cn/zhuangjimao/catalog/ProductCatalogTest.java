package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/** SYS-T01 catalog subset: public HTTP against the explicitly isolated MySQL test database. */
class ProductCatalogTest {
    private static ConfigurableApplicationContext app;
    private static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void start() {
        app = ProductBrowserTest.start(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
    }

    @AfterAll
    static void stop() {
        if (app != null) app.close();
    }

    @Test
    void guestCanBrowseEachOfTheEightHardwareCategories() throws Exception {
        for (String category : new String[]{"CPU", "MOTHERBOARD", "GPU", "MEMORY", "SSD", "PSU", "CASE", "COOLER"}) {
            var response = ProductBrowserTest.get(app, "/api/products?category=" + category);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode products = json.readTree(response.body()).path("data");
            assertThat(products).isNotEmpty();
            for (JsonNode product : products) {
                assertThat(product.path("category").asText()).isEqualTo(category);
            }
        }
    }

    @Test
    void keywordSearchCombinesWithCategoryAndTreatsSqlWildcardsLiterally() throws Exception {
        var response = ProductBrowserTest.get(app, "/api/products?category=MEMORY&keyword=kf560c36bbek2-32");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode matches = json.readTree(response.body()).path("data");
        assertThat(matches.size()).isEqualTo(1);
        assertThat(matches.get(0).path("sku").asText()).isEqualTo("KINGSTON-KF560C36BBEK2-32");
        for (String query : new String[]{"category=CPU&keyword=Kingston", "keyword=no-such-product", "keyword=%25", "keyword=%27%20OR%201%3D1--"}) {
            var empty = ProductBrowserTest.get(app, "/api/products?" + query);
            assertThat(empty.statusCode()).isEqualTo(200);
            assertThat(json.readTree(empty.body()).path("data").size()).isZero();
        }
    }

    @Test
    void detailsKeepSalesIdentityAndSeparateKitCapacityFromStickCount() throws Exception {
        JsonNode memory = json.readTree(ProductBrowserTest.get(app, "/api/products?category=MEMORY").body()).path("data").get(0);
        var response = ProductBrowserTest.get(app, "/api/products/" + memory.path("id").asLong());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode detail = json.readTree(response.body()).path("data");
        assertThat(detail.path("product")).isEqualTo(memory);
        assertThat(detail.path("description").asText()).contains("两条");
        assertThat(parameter(detail, "totalCapacity").path("value").asInt()).isEqualTo(32);
        assertThat(parameter(detail, "totalCapacity").path("unit").asText()).isEqualTo("GB");
        assertThat(parameter(detail, "stickCount").path("value").asInt()).isEqualTo(2);
        assertThat(parameter(detail, "generation").path("value").asText()).isEqualTo("DDR5");
        assertThat(parameter(detail, "stickCount").path("sourceUrl").asText()).endsWith("KF560C36BBEK2-32.pdf");
    }

    private static JsonNode parameter(JsonNode detail, String code) {
        for (JsonNode parameter : detail.path("parameters")) {
            if (parameter.path("code").asText().equals(code)) return parameter;
        }
        throw new AssertionError("Missing parameter: " + code);
    }

    @Test
    void everyListedProductHasDetailsAndUnitsAndSetsAreExplicit() throws Exception {
        JsonNode products = json.readTree(ProductBrowserTest.get(app, "/api/products").body()).path("data");
        for (JsonNode product : products) {
            var response = ProductBrowserTest.get(app, "/api/products/" + product.path("id").asLong());
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode detail = json.readTree(response.body()).path("data");
            assertThat(detail.path("product")).isEqualTo(product);
            assertThat(detail.path("description").asText()).isNotBlank();
            assertThat(detail.path("parameters")).isNotEmpty();
            if (product.path("category").asText().equals("SSD")) {
                assertThat(parameter(detail, "capacity").path("value").asInt()).isEqualTo(1000);
                assertThat(parameter(detail, "capacity").path("unit").asText()).isEqualTo("GB");
                assertThat(parameter(detail, "originalCapacity").path("unit").asText()).isEqualTo("TB");
                assertThat(parameter(detail, "formFactor").path("value").asText()).isEqualTo("M.2 2280");
            }
            if (product.path("category").asText().equals("CASE")) {
                assertThat(parameter(detail, "supportedMotherboardFormFactors").path("value").isArray()).isTrue();
                assertThat(parameter(detail, "supportedMotherboardFormFactors").path("value").size()).isEqualTo(3);
                assertThat(parameter(detail, "maxGpuLength").path("unit").asText()).isEqualTo("mm");
            }
        }
    }

    @Test
    void invalidQueriesAndMissingProductsReturnStructuredResults() throws Exception {
        for (String path : new String[]{"/api/products?category=PERIPHERAL", "/api/products?keyword=" + "a".repeat(101),
                "/api/products/abc", "/api/products/0", "/api/products/-1", "/api/products/999999999999999999999"}) {
            var response = ProductBrowserTest.get(app, path);
            assertThat(response.statusCode()).as(path).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("code").asText()).startsWith("INVALID_");
            assertThat(json.readTree(response.body()).has("data")).isFalse();
        }
        var missing = ProductBrowserTest.get(app, "/api/products/9223372036854775807");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).path("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void currentDatabaseChangesAreVisibleAndOffSaleProductsCannotBeSelected() throws Exception {
        JsonNode original = json.readTree(ProductBrowserTest.get(app, "/api/products?keyword=KF560C36BBEK2-32").body()).path("data").get(0);
        long id = original.path("id").asLong();
        // Fixture mutation is the stimulus; all behavior is asserted through the public HTTP interface.
        // Application startup above enforces the dedicated test URL/account before this connection is opened.
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
             var update = connection.prepareStatement("UPDATE products SET price=?, stock=?, status=? WHERE id=?")) {
            try {
                update.setBigDecimal(1, new java.math.BigDecimal("910.50"));
                update.setInt(2, 0);
                update.setString(3, "ON_SALE");
                update.setLong(4, id);
                update.executeUpdate();
                JsonNode detail = json.readTree(ProductBrowserTest.get(app, "/api/products/" + id).body()).path("data");
                assertThat(detail.path("product").path("price").asText()).isEqualTo("910.50");
                assertThat(detail.path("product").path("stock").asInt()).isZero();
                JsonNode list = json.readTree(ProductBrowserTest.get(app, "/api/products?keyword=KF560C36BBEK2-32").body()).path("data");
                assertThat(list.get(0).path("price").asText()).isEqualTo("910.50");
                update.setString(3, "OFF_SALE");
                update.executeUpdate();
                var unavailable = ProductBrowserTest.get(app, "/api/products/" + id);
                assertThat(unavailable.statusCode()).isEqualTo(409);
                assertThat(json.readTree(unavailable.body()).path("code").asText()).isEqualTo("PRODUCT_NOT_SELECTABLE");
                var candidates = ProductBrowserTest.get(app, "/api/products?category=MEMORY");
                assertThat(json.readTree(candidates.body()).path("data").size()).isZero();
            } finally {
                update.setBigDecimal(1, new java.math.BigDecimal(original.path("price").asText()));
                update.setInt(2, original.path("stock").asInt());
                update.setString(3, original.path("status").asText());
                update.setLong(4, id);
                update.executeUpdate();
            }
        }
    }
}
