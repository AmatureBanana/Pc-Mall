package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes=MallApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PerformanceDataTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Test void migratedReferencesMatchFrozenRenderingEvidenceAndRatedProductSpecifications() throws Exception {
        String[][] expected={{"INTEL-I5-14600K","30","59.954021"},{"INTEL-I5-14600KF","33","59.404843"},{"NVIDIA-RTX4070SUPER-FE","395","86.157831"},
                {"AMD-R7-7800X3D","284","55.306582"},{"AMD-RX7800XT","133","54.413540"}};
        for (var e:expected) {
            var detail=detail(e[0]); var performance=detail.path("performance");
            assertThat(performance.path("state").asText()).isEqualTo("VALID");
            assertThat(performance.path("score").asText()).isEqualTo(e[2]);
            assertThat(performance.path("reference").path("sampleCount").asInt()).isEqualTo(Integer.parseInt(e[1]));
            assertThat(performance.path("reference").path("sampleIds").size()).isEqualTo(Integer.parseInt(e[1]));
            assertThat(performance.path("reference").path("snapshotSha256").asText()).isEqualTo("7e0875527aea34d96e99c933d1e82c62a20dac0a343720d252b12c21228812dd");
        }
        var memory=capacityReference("KINGSTON-KF560C36BBE2-16","21.429325","76.990741");
        assertThat(input(memory,"dataRate").path("value").asInt()).isEqualTo(6000);
        assertThat(input(memory,"casLatency").path("value").asInt()).isEqualTo(36);
        assertThat(input(memory,"trcd").path("value").asInt()).isEqualTo(44);
        assertThat(input(memory,"trp").path("value").asInt()).isEqualTo(44);
        var samsung=capacityReference("SAMSUNG-MZ-V9P1T0BW","59.731544","100.000000");
        assertThat(input(samsung,"sequentialRead").path("value").asInt()).isEqualTo(7450);
        assertThat(input(samsung,"sequentialWrite").path("value").asInt()).isEqualTo(6900);
        var wd=capacityReference("WD-WDS100T2X0E","59.731544","100.000000");
        assertThat(input(wd,"sequentialRead").path("value").asInt()).isEqualTo(7300);
        assertThat(input(wd,"sequentialWrite").path("value").asInt()).isEqualTo(6300);
    }

    private JsonNode capacityReference(String sku,String score,String performanceScore) throws Exception {
        var performance=detail(sku).path("performance");
        assertThat(performance.path("mappingVersion").asText()).isEqualTo("capacity-priority-v1");
        assertThat(performance.path("state").asText()).isEqualTo("VALID");
        assertThat(performance.path("score").asText()).isEqualTo(score);
        assertThat(performance.path("performanceScore").asText()).isEqualTo(performanceScore);
        return performance.path("reference");
    }

    private JsonNode input(JsonNode reference,String code) {
        for (var entry:reference.path("inputs")) if (entry.path("code").asText().equals(code)) {
            assertThat(entry.path("sourceUrl").asText()).startsWith("https://");
            return entry;
        }
        throw new AssertionError("Missing rating input: "+code);
    }
    @Test void threeScenarioManifestsReferenceDistinctRealSaleUnitsWithDemoPricesAndSufficientInputs() throws Exception {
        var scenarios=json.readTree(Files.readString(Path.of("data/performance-v1/scenarios.json")));
        for (String scenario:List.of("office","gaming","creation")) {
            var selection=scenarios.path(scenario); Set<String> categories=new HashSet<>(); BigDecimal total=BigDecimal.ZERO;
            for (var sku:selection.path("skus")) {
                var detail=detail(sku.asText()); var product=detail.path("product");
                assertThat(categories.add(product.path("category").asText())).isTrue();
                assertThat(product.path("stock").asInt()).isPositive();
                assertThat(product.path("priceNote").asText()).contains("演示");
                total=total.add(new BigDecimal(product.path("price").asText()));
                if (Set.of("CPU","GPU","MEMORY","SSD").contains(product.path("category").asText())) assertThat(detail.path("performance").path("state").asText()).isEqualTo("VALID");
                for (var parameter:detail.path("parameters")) assertThat(parameter.path("value").isNull()).as("%s/%s",sku,parameter.path("code")).isFalse();
            }
            assertThat(categories).contains("CPU","MOTHERBOARD","MEMORY","SSD","PSU","CASE","COOLER");
            assertThat(categories.contains("GPU")).isEqualTo(!scenario.equals("office"));
            assertThat(total).isEqualByComparingTo(selection.path("demoTotalCny").asText());
        }
    }
    JsonNode detail(String sku) throws Exception {
        var list=get("/api/products?keyword="+sku); JsonNode product=null;
        for (var entry:list) if (entry.path("sku").asText().equals(sku)) product=entry;
        assertThat(product).as(sku).isNotNull();
        return get("/api/products/"+product.path("id").asLong());
    }
    JsonNode get(String path) throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
}
