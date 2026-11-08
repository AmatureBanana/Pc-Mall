package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityFailureTest {
    @Test
    void anUnavailableDatabaseReturnsAnExplicitErrorForTheRequestedRevision() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("unavailable-compatibility-test", Map.of(
                "TEST_DB_URL", "jdbc:mysql://127.0.0.1:1/pc_mall_test?connectTimeout=250",
                "TEST_DB_USERNAME", "pc_mall_test", "TEST_DB_PASSWORD", "unused")));
        var application = new SpringApplication(MallApplication.class);
        application.setEnvironment(environment);
        application.setAdditionalProfiles("test");
        try (var app = application.run("--server.port=0", "--spring.flyway.enabled=false",
                "--spring.datasource.hikari.initialization-fail-timeout=-1", "--spring.datasource.hikari.connection-timeout=250")) {
            int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
            var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
            var json = new ObjectMapper();
            var csrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            var token = json.readTree(csrf.body()).path("data");
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/compatibility/check"))
                    .header("Content-Type", "application/json").header(token.path("headerName").asText(), token.path("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"revision\":42,\"CPU\":1}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(503);
            var data = json.readTree(response.body()).path("data");
            assertThat(data.path("status").asText()).isEqualTo("ERROR");
            assertThat(data.path("revision").asInt()).isEqualTo(42);
            assertThat(data.path("corePassed").asBoolean()).isFalse();
            assertThat(data.path("catalogVersion").isNull()).isTrue();
            assertThat(data.path("checks")).isEmpty();
            assertThat(data.path("unexecutedRules")).hasSize(9);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(response.body()).doesNotContain("SQLException", "unused", "jdbc:mysql");
        }
    }
}
