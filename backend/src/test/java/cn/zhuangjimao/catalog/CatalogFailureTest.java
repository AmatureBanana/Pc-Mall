package cn.zhuangjimao.catalog;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFailureTest {
    @Test
    void unavailableMySqlIsA503ErrorRatherThanAnEmptySuccess() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("unavailable-test-instance", Map.of(
                "TEST_DB_URL", "jdbc:mysql://127.0.0.1:1/pc_mall_test?connectTimeout=250",
                "TEST_DB_USERNAME", "pc_mall_test", "TEST_DB_PASSWORD", "unused")));
        var application = new SpringApplication(MallApplication.class);
        application.setEnvironment(environment);
        application.setAdditionalProfiles("test");
        // Only this fault-injection startup disables migration, so HTTP remains reachable with MySQL down.
        try (var app = application.run("--server.port=0", "--spring.flyway.enabled=false",
                "--spring.datasource.hikari.initialization-fail-timeout=-1",
                "--spring.datasource.hikari.connection-timeout=250")) {
            var response = ProductBrowserTest.get(app, "/api/products");
            assertThat(response.statusCode()).isEqualTo(503);
            var body = new ObjectMapper().readTree(response.body());
            assertThat(body.path("code").asText()).isEqualTo("CATALOG_UNAVAILABLE");
            assertThat(body.has("data")).isFalse();
            assertThat(response.body()).doesNotContain("SQLException", "unused", "jdbc:mysql");
        }
    }
}
