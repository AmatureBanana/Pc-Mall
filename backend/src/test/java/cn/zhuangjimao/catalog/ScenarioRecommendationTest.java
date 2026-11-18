package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ScenarioRecommendationTest.FaultConfiguration.class)
class ScenarioRecommendationTest {
    private static final List<String> SCENARIOS = List.of("OFFICE", "GAMING", "CREATION");
    @LocalServerPort int port;
    @Autowired CompatibilityService compatibility;
    @Autowired JdbcTemplate db;
    @Autowired CompatibilityReadFault fault;
    private final ObjectMapper json = new ObjectMapper();

    @Test void gamingAndCreationReturnScoredPlansWithDedicatedGraphics() throws Exception {
        for (var scenario : new String[]{"GAMING", "CREATION"}) {
            var response = post("{\"scenario\":\"" + scenario + "\",\"budget\":\"20000.00\"}");
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            var result = json.readTree(response.body()).path("data");
            assertThat(result.path("scenario").asText()).isEqualTo(scenario);
            assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
            assertThat(result.path("compatibility").path("corePassed").asBoolean()).isTrue();
            assertThat(result.path("compatibility").path("complete").asBoolean()).isTrue();
            assertThat(result.path("items")).hasSize(8);
            assertThat(result.path("integratedGraphicsCpuId").isNull()).isTrue();
            assertThat(result.path("items").findValuesAsText("category")).contains("GPU");
            assertSaleableItemsAndPrice(result);
            assertThat(new BigDecimal(result.path("scores").path("cpu").asText())).isGreaterThanOrEqualTo(BigDecimal.valueOf(35));
            assertThat(new BigDecimal(result.path("scores").path("memory").asText())).isGreaterThanOrEqualTo(BigDecimal.valueOf(80));
            assertThat(new BigDecimal(result.path("scores").path("ssd").asText())).isGreaterThanOrEqualTo(BigDecimal.valueOf(75));
            assertThat(new BigDecimal(result.path("scores").path("gpu").asText()))
                    .isGreaterThanOrEqualTo(BigDecimal.valueOf(scenario.equals("GAMING") ? 40 : 35));
            var total = new BigDecimal(result.path("total").asText());
            assertThat(total).isLessThanOrEqualTo(BigDecimal.valueOf(20000));
            assertThat(read(scenario, total.toPlainString()).path("remaining").asText()).isEqualTo("0.00");
            assertThat(read(scenario, "20000.00").path("items")).isEqualTo(result.path("items"));
        }
    }

    @Test void guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary() throws Exception {
        var before = ProductBrowserTest.get((org.springframework.context.ConfigurableApplicationContext) context, "/api/products").body();
        var response = post("{\"scenario\":\"OFFICE\",\"budget\":\"10000.00\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var result = json.readTree(response.body()).path("data");
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertSaleableItemsAndPrice(result);
        var selections = new EnumMap<HardwareCategory, Long>(HardwareCategory.class);
        for (var item : result.path("items")) {
            selections.put(HardwareCategory.valueOf(item.path("category").asText()), item.path("productId").asLong());
            assertThat(item.path("state").asText()).isEqualTo("VALID");
        }
        Long igpu = result.path("integratedGraphicsCpuId").isNull() ? null : result.path("integratedGraphicsCpuId").asLong();
        assertThat(compatibility.check(0, selections, igpu, result.path("memoryQuantity").asInt()).corePassed()).isTrue();
        assertThat(new BigDecimal(result.path("total").asText())).isLessThanOrEqualTo(new BigDecimal("10000"));
        assertThat(json.readTree(post("{\"scenario\":\"OFFICE\",\"budget\":\"10000.00\"}").body()).path("data").path("items"))
                .isEqualTo(result.path("items"));
        var exact = json.readTree(post("{\"scenario\":\"OFFICE\",\"budget\":\"" + result.path("total").asText() + "\"}").body()).path("data");
        assertThat(exact.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(new BigDecimal(exact.path("remaining").asText())).isEqualByComparingTo("0");
        assertThat(ProductBrowserTest.get((org.springframework.context.ConfigurableApplicationContext) context, "/api/products").body()).isEqualTo(before);
    }

    @Autowired org.springframework.context.ApplicationContext context;

    @Test void invalidInputAndProvenPriceLowerBoundNeverInventAPlan() throws Exception {
        for (String budget : new String[]{"0", "-1", "NaN", "1.001", "10000000000", ""}) {
            var response = post("{\"scenario\":\"OFFICE\",\"budget\":\"" + budget + "\"}");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("INVALID_INPUT");
        }
        assertThat(post("{\"scenario\":\"OFFICE\",\"budget\":\"20\",\"budget\":\"30\"}").statusCode()).isEqualTo(400);
        assertThat(post("{\"scenario\":\"UNKNOWN\",\"budget\":\"8000\"}").statusCode()).isEqualTo(400);
        for (var payload : List.of("", "{}", "{\"scenario\":\"GAMING\"}", "{\"budget\":\"8000\"}")) {
            var response = post(payload);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("INVALID_INPUT");
        }
        assertFailures(400, "INVALID_INPUT", "0");
        assertFailures(200, "BUDGET_INSUFFICIENT", "1.00");
    }

    @Test void unavailableOrUnscoredCpuCannotBecomeASuccess() throws Exception {
        var cpus = db.queryForList("SELECT id,stock,status FROM products WHERE category='CPU'");
        try {
            db.update("UPDATE products SET stock=0 WHERE category='CPU'");
            assertFailures(200, "NO_AVAILABLE_PRODUCT", "20000");
            db.update("UPDATE products SET stock=1,status='OFF_SALE' WHERE category='CPU'");
            assertFailures(200, "NO_AVAILABLE_PRODUCT", "20000");
        } finally {
            for (var cpu : cpus) db.update("UPDATE products SET stock=?,status=? WHERE id=?", cpu.get("stock"), cpu.get("status"), cpu.get("id"));
        }
        var references = db.queryForList("SELECT product_id,reference_data FROM product_performance WHERE product_id IN (SELECT id FROM products WHERE category='CPU')");
        try {
            db.update("UPDATE product_performance SET reference_data=JSON_SET(reference_data,'$.valid',false) WHERE product_id IN (SELECT id FROM products WHERE category='CPU')");
            assertFailures(200, "INSUFFICIENT_DATA", "20000");
        } finally { for (var reference : references) db.update("UPDATE product_performance SET reference_data=? WHERE product_id=?", reference.get("reference_data"), reference.get("product_id")); }
    }

    @Test void gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics() throws Exception {
        var gpus = db.queryForList("SELECT id,stock FROM products WHERE category='GPU'");
        var cpus = parametersFor("CPU");
        try {
            db.update("UPDATE products SET stock=0 WHERE category='GPU'");
            assertThat(read("OFFICE", "20000").path("status").asText()).isEqualTo("SUCCESS");
            assertFailure("GAMING", 200, "NO_AVAILABLE_PRODUCT", "20000");
            assertFailure("CREATION", 200, "NO_AVAILABLE_PRODUCT", "20000");
            setParameter(cpus, "integratedGraphics", json.getNodeFactory().booleanNode(false));
            assertFailure("OFFICE", 200, "NO_AVAILABLE_PRODUCT", "20000");
            setParameter(cpus, "integratedGraphics", json.nullNode());
            assertFailure("OFFICE", 200, "INSUFFICIENT_DATA", "20000");
        } finally {
            restoreParameters(cpus);
            for (var gpu : gpus) db.update("UPDATE products SET stock=? WHERE id=?", gpu.get("stock"), gpu.get("id"));
        }

        var references = db.queryForList("SELECT product_id,reference_data FROM product_performance WHERE product_id IN (SELECT id FROM products WHERE category='GPU')");
        try {
            db.update("""
                    UPDATE product_performance SET reference_data=JSON_SET(reference_data,'$.valid',false)
                    WHERE product_id IN (SELECT id FROM products WHERE category='GPU')
                    """);
            assertFailure("GAMING", 200, "INSUFFICIENT_DATA", "20000");
            assertFailure("CREATION", 200, "INSUFFICIENT_DATA", "20000");
            db.update("""
                    UPDATE product_performance SET reference_data=JSON_SET(reference_data,'$.rawScore','1100','$.valid',CAST('true' AS JSON))
                    WHERE product_id IN (SELECT id FROM products WHERE category='GPU')
                    """);
            assertThat(read("OFFICE", "20000").path("status").asText()).isEqualTo("SUCCESS");
            assertFailure("GAMING", 200, "INSUFFICIENT_DATA", "20000");
            var creation = read("CREATION", "20000");
            assertThat(creation.path("status").asText()).isEqualTo("SUCCESS");
            assertThat(new BigDecimal(creation.path("scores").path("gpu").asText())).isEqualByComparingTo("35");
        } finally { for (var reference : references) db.update("UPDATE product_performance SET reference_data=? WHERE product_id=?", reference.get("reference_data"), reference.get("product_id")); }
    }

    @Test void confirmedConflictMissingParametersAndUnprovenSearchAreDistinct() throws Exception {
        var boards = parametersFor("MOTHERBOARD");
        String prefix = "task22-search-" + UUID.randomUUID();
        try {
            setParameter(boards, "socket", json.nullNode());
            assertFailures(200, "INSUFFICIENT_DATA", "20000");
            setParameter(boards, "socket", json.getNodeFactory().textNode("sTRX4"));
            assertFailures(200, "NO_COMPATIBLE_COMBINATION", "20000");
            for (int index = 0; index < 48; index++) {
                db.update("""
                        INSERT INTO products(sku,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
                        SELECT ?,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version
                        FROM products WHERE sku='DEEPCOOL-CC560'
                        """, prefix + "-" + index);
            }
            db.update("""
                    INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on)
                    SELECT p.id,s.description,s.parameters,s.data_version,s.verified_on FROM products p
                    CROSS JOIN product_specifications s JOIN products original ON original.id=s.product_id
                    WHERE original.sku='DEEPCOOL-CC560' AND p.sku LIKE CONCAT(?, '%')
                    """, prefix);
            assertFailures(200, "NO_RESULT_FOUND", "20000");
        } finally {
            db.update("DELETE FROM product_specifications WHERE product_id IN (SELECT id FROM products WHERE sku LIKE CONCAT(?, '%'))", prefix);
            db.update("DELETE FROM products WHERE sku LIKE CONCAT(?, '%')", prefix);
            restoreParameters(boards);
        }
    }

    @Test void caseClearanceFailureNeverProducesASuccess() throws Exception {
        var cases = parametersFor("CASE");
        try {
            setParameter(cases, "maxCoolerHeight", json.getNodeFactory().numberNode(50));
            assertFailures(200, "NO_COMPATIBLE_COMBINATION", "20000");
        } finally { restoreParameters(cases); }
    }

    @Test void authoritativeCompatibilityReadErrorsAndStaleReferencesNeverReturnSuccess() throws Exception {
        try {
            fault.mode = Fault.MISSING;
            assertFailures(200, "REVALIDATION_REQUIRED", "20000");
            fault.mode = Fault.UNAVAILABLE;
            assertFailures(503, "SYSTEM_ERROR", "20000");
        } finally { fault.mode = Fault.NORMAL; }
    }

    private List<Map<String, Object>> parametersFor(String category) {
        return db.queryForList("""
                SELECT product_id,parameters FROM product_specifications
                WHERE product_id IN (SELECT id FROM products WHERE category=? AND status='ON_SALE')
                """, category);
    }

    private void setParameter(List<Map<String, Object>> products, String code, JsonNode value) throws Exception {
        for (var product : products) {
            var params = json.readTree(product.get("parameters").toString());
            for (var param : params) if (param.path("code").asText().equals(code))
                ((ObjectNode) param).set("value", value);
            db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", params.toString(), product.get("product_id"));
        }
    }

    private void restoreParameters(List<Map<String, Object>> products) {
        for (var product : products)
            db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", product.get("parameters"), product.get("product_id"));
    }

    private void assertSaleableItemsAndPrice(JsonNode result) {
        var categories = EnumSet.noneOf(HardwareCategory.class);
        BigDecimal total = BigDecimal.ZERO;
        for (var item : result.path("items")) {
            assertThat(categories.add(HardwareCategory.valueOf(item.path("category").asText()))).isTrue();
            assertThat(item.path("state").asText()).isEqualTo("VALID");
            var product = item.path("product");
            assertThat(product.path("status").asText()).isEqualTo("ON_SALE");
            assertThat(product.path("stock").asInt()).isGreaterThanOrEqualTo(item.path("quantity").asInt());
            total = total.add(new BigDecimal(item.path("amount").asText()));
        }
        assertThat(total).isEqualByComparingTo(result.path("total").asText());
        assertThat(total).isLessThanOrEqualTo(new BigDecimal(result.path("budget").asText()));
        assertThat(result.path("compatibility").path("unexecutedExtensions")).hasSize(6);
    }

    private JsonNode read(String scenario, String budget) throws Exception {
        return json.readTree(post(body(scenario, budget)).body()).path("data");
    }

    private void assertFailures(int code, String status, String budget) throws Exception {
        for (var scenario : SCENARIOS) assertFailure(scenario, code, status, budget);
    }

    private void assertFailure(String scenario, int code, String status, String budget) throws Exception {
        var response = post(body(scenario, budget));
        assertThat(response.statusCode()).as(scenario + ": " + response.body()).isEqualTo(code);
        var data = json.readTree(response.body()).path("data");
        assertThat(data.path("status").asText()).as(scenario + ": " + data).isEqualTo(status);
        assertThat(data.path("items")).isEmpty();
        for (var key : List.of("total", "remaining", "score", "scores", "weights", "compatibility")) assertThat(data.path(key).isNull()).isTrue();
        assertThat(data.has("minimumBudget")).isFalse();
    }

    private static String body(String scenario, String budget) {
        return "{\"scenario\":\"" + scenario + "\",\"budget\":\"" + budget + "\"}";
    }

    private HttpResponse<String> post(String body) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var csrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/csrf")).build(), HttpResponse.BodyHandlers.ofString());
        var token = json.readTree(csrf.body()).path("data");
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/recommendations"))
                .header("Content-Type", "application/json").header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    enum Fault { NORMAL, MISSING, UNAVAILABLE }

    @TestConfiguration
    static class FaultConfiguration {
        @Bean CompatibilityReadFault compatibilityReadFault() { return new CompatibilityReadFault(); }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static class CompatibilityReadFault implements Interceptor {
        volatile Fault mode = Fault.NORMAL;

        @Override public Object intercept(Invocation invocation) throws Throwable {
            var statement = (MappedStatement) invocation.getArgs()[0];
            if (statement.getId().equals("cn.zhuangjimao.compatibility.CompatibilityMapper.find")) {
                if (mode == Fault.MISSING) return List.of();
                if (mode == Fault.UNAVAILABLE) throw new java.sql.SQLException("Compatibility catalog read failed");
            }
            return invocation.proceed();
        }
    }
}
