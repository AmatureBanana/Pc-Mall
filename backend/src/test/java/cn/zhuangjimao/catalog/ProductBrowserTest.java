package cn.zhuangjimao.catalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** SYS-T01 subset: real HTTP, real isolated MySQL, no persistence mocks. */
class ProductBrowserTest {
    @Test
    void guestSeesMigratedProductsAndRestartDoesNotDuplicateThem() throws Exception {
        String url = required("TEST_DB_URL");
        String user = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        JsonNode first;
        try (var app = start(url, user, password)) {
            var response = get(app, "/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            first = new ObjectMapper().readTree(response.body());
            assertThat(first.path("data").size()).isEqualTo(12);
            JsonNode product = first.path("data").get(0);
            assertThat(product.path("name").asText()).isEqualTo("Intel Core i5-14600KF");
            assertThat(product.path("category").asText()).isEqualTo("CPU");
            assertThat(product.path("price").asText()).isEqualTo("1499.00");
            assertThat(product.path("stock").asInt()).isEqualTo(12);
            assertThat(product.path("sourceUrl").asText()).startsWith("https://www.intel.com/");
            assertThat(product.path("priceNote").asText()).contains("演示");
        }
        try (var app = start(url, user, password)) {
            var response = get(app, "/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new ObjectMapper().readTree(response.body())).isEqualTo(first);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required; no demo or in-memory fallback is allowed");
        }
        return value;
    }

    static ConfigurableApplicationContext start(String url, String user, String password) {
        var application = new SpringApplication(MallApplication.class);
        application.setAdditionalProfiles("test");
        return application.run("--server.port=0", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password);
    }

    static HttpResponse<String> get(ConfigurableApplicationContext app, String path) throws Exception {
        int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
