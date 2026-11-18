package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.util.EnumMap;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OfficeRecommendationTest {
    @LocalServerPort int port;
    @Autowired CompatibilityService compatibility;
    @Autowired JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper();

    @Test void guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary() throws Exception {
        var before = ProductBrowserTest.get((org.springframework.context.ConfigurableApplicationContext) context, "/api/products").body();
        var response = post("{\"scenario\":\"OFFICE\",\"budget\":\"10000.00\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var result = json.readTree(response.body()).path("data");
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
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

    @Test void invalidInputsAndBoundedSearchFailureNeverInventAPlan() throws Exception {
        for (String budget : new String[]{"0", "-1", "NaN", "1.001", "10000000000", ""}) {
            var response = post("{\"scenario\":\"OFFICE\",\"budget\":\"" + budget + "\"}");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("INVALID_INPUT");
        }
        assertThat(post("{\"scenario\":\"OFFICE\",\"budget\":\"20\",\"budget\":\"30\"}").statusCode()).isEqualTo(400);
        var failure = json.readTree(post("{\"scenario\":\"OFFICE\",\"budget\":\"1.00\"}").body()).path("data");
        assertThat(failure.path("status").asText()).isEqualTo("NO_RESULT_FOUND");
        assertThat(failure.path("items").isEmpty()).isTrue();
        assertThat(failure.path("total").isNull()).isTrue();
    }

    @Test void unavailableOrUnscoredCpuCannotBecomeASuccess() throws Exception {
        var cpus = db.queryForList("SELECT id,stock FROM products WHERE category='CPU'");
        try {
            db.update("UPDATE products SET stock=0 WHERE category='CPU'");
            var result = json.readTree(post("{\"scenario\":\"OFFICE\",\"budget\":\"10000\"}").body()).path("data");
            assertThat(result.path("status").asText()).isEqualTo("NO_AVAILABLE_PRODUCT");
        } finally { for (var cpu : cpus) db.update("UPDATE products SET stock=? WHERE id=?", cpu.get("stock"), cpu.get("id")); }
        var references = db.queryForList("SELECT product_id,reference_data FROM product_performance WHERE product_id IN (SELECT id FROM products WHERE category='CPU')");
        try {
            db.update("UPDATE product_performance SET reference_data=JSON_SET(reference_data,'$.valid',false) WHERE product_id IN (SELECT id FROM products WHERE category='CPU')");
            var result = json.readTree(post("{\"scenario\":\"OFFICE\",\"budget\":\"10000\"}").body()).path("data");
            assertThat(result.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        } finally { for (var reference : references) db.update("UPDATE product_performance SET reference_data=? WHERE product_id=?", reference.get("reference_data"), reference.get("product_id")); }
    }

    private HttpResponse<String> post(String body) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var csrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/csrf")).build(), HttpResponse.BodyHandlers.ofString());
        var token = json.readTree(csrf.body()).path("data");
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/recommendations"))
                .header("Content-Type", "application/json").header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
