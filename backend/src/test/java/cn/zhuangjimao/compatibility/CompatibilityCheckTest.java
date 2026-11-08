package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.MallApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** F03 public HTTP seam against the guarded, real pc_mall_test schema. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CompatibilityCheckTest {
    private static final ObjectMapper json = new ObjectMapper();

    // V2/V4 fixtures: 1 Intel i5-14600KF (LGA1700), 2 AMD 7800X3D (AM5), 3 ASUS TUF B760M
    // (LGA1700 · Micro-ATX · DDR5 · M.2 PCIe/NVMe), 7 Kingston DDR5 2×16GB, 8 AK400,
    // 9 Samsung 990 PRO (M.2 2280 · PCIe · NVMe), 11 RM750e, 12 CC560 (ATX/M-ATX/Mini-ITX).
    private static final String COMPLETE = """
            {"revision":3,"CPU":1,"MOTHERBOARD":3,"MEMORY":7,"SSD":9,"PSU":11,"COOLER":8,"CASE":12}""";

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;

    @Test
    void guestReadsCurrentDatabaseParametersAndSeesEachFinishedRuleApart() throws Exception {
        var response = check(COMPLETE);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data.path("revision").asInt()).isEqualTo(3);
        assertThat(data.path("ruleVersion").asText()).isNotBlank();
        assertThat(data.path("checkedAt").asText()).isNotBlank();
        // C06–C10 never ran, so a full-machine pass is impossible by construction.
        assertThat(data.path("corePassed").asBoolean()).isFalse();
        assertThat(data.path("checks").findValuesAsText("rule")).containsExactly("C01", "C02", "C03", "C04", "C05");
        for (String rule : List.of("C01", "C02", "C03", "C04", "C05")) {
            assertThat(rule(data, rule).path("state").asText()).as(rule).isEqualTo("PASS");
            assertThat(rule(data, rule).path("message").asText()).as(rule).isNotBlank();
            assertThat(rule(data, rule).path("categories").isArray()).as(rule).isTrue();
        }
        assertThat(data.path("unexecutedRules").findValuesAsText("rule")).containsExactly("C06", "C07", "C08", "C09", "C10");
        // C01–C05 need six parts; GPU and PSU are not yet part of any executed rule.
        assertThat(missing(data)).isEmpty();
    }

    @Test
    void aRealDatabaseConflictFailsOneRuleWithoutClearingTheOthers() throws Exception {
        long board = cloneProduct(3);
        try {
            db.update("""
                UPDATE product_specifications SET parameters=JSON_SET(parameters,'$[0].value','AM5')
                WHERE product_id=?
                """, board);
            JsonNode data = json.readTree(check(COMPLETE.replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board))
                    .body()).path("data");

            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("FAIL");
            assertThat(rule(data, "C01").path("message").asText()).contains("LGA1700", "AM5");
            assertThat(rule(data, "C02").path("state").asText()).isEqualTo("PASS");
            assertThat(rule(data, "C04").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("checks")).hasSize(5);
            assertThat(data.path("corePassed").asBoolean()).isFalse();
        } finally { deleteProduct(board); }
    }

    @Test
    void anM2SlotThatOnlyLinksSataFailsNvmeOnProtocolRatherThanOnFormFactor() throws Exception {
        long board = cloneProduct(3);
        try {
            db.update("""
                UPDATE product_specifications SET parameters=JSON_SET(parameters, CONCAT('$[', ?, '].value'),
                    JSON_ARRAY(JSON_OBJECT('formFactor','M.2 2280','interface','SATA','protocol','AHCI')))
                WHERE product_id=?
                """, slotIndex(board, "ssdSlots"), board);
            JsonNode data = json.readTree(check(COMPLETE.replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board))
                    .body()).path("data");

            JsonNode c05 = rule(data, "C05");
            assertThat(c05.path("state").asText()).isEqualTo("FAIL");
            assertThat(c05.path("message").asText()).contains("M.2 2280", "NVMe");
            assertThat(c05.path("categories").toString()).contains("MOTHERBOARD", "SSD");
            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("PASS");
        } finally { deleteProduct(board); }
    }

    @Test
    void unselectedPartsArePendingAndAMissingProductIsReportedAsMissing() throws Exception {
        JsonNode partial = json.readTree(check("""
                {"revision":5,"CPU":1}""").body()).path("data");
        assertThat(rule(partial, "C01").path("state").asText()).isEqualTo("PENDING");
        assertThat(missing(partial)).containsExactly("MOTHERBOARD", "MEMORY", "SSD", "PSU", "CASE", "COOLER");
        assertThat(partial.path("missing").findValuesAsText("state")).containsOnly("NOT_SELECTED");
        assertThat(partial.path("missing")).allSatisfy(entry -> assertThat(entry.path("label").asText()).isNotBlank());
        assertThat(partial.path("corePassed").asBoolean()).isFalse();

        // Both sockets are known from the database and genuinely differ.
        JsonNode mismatched = json.readTree(check("""
                {"revision":6,"CPU":2,"MOTHERBOARD":3}""").body()).path("data");
        assertThat(rule(mismatched, "C01").path("state").asText()).isEqualTo("FAIL");
        assertThat(rule(mismatched, "C02").path("state").asText()).isEqualTo("PENDING");

        JsonNode absent = json.readTree(check("""
                {"revision":7,"CPU":9007199254740991,"MOTHERBOARD":3}""").body()).path("data");
        assertThat(rule(absent, "C01").path("state").asText()).isEqualTo("PENDING");
        assertThat(missing(absent)).contains("CPU");
        assertThat(absent.path("missing").findValuesAsText("state")).contains("NOT_FOUND");
        assertThat(absent.path("corePassed").asBoolean()).isFalse();
    }

    @Test
    void rejectsClientSuppliedParametersCategoriesAndPassFlags() throws Exception {
        for (String body : List.of(
                """
                {"revision":1,"CPU":{"productId":1,"socket":"LGA1700"}}""",
                """
                {"revision":1,"CPU":1,"compatibility":"PASSED"}""",
                """
                {"revision":1,"CPU":1,"corePassed":true}""",
                """
                {"CPU":1}""",
                """
                {"revision":-1,"CPU":1}""",
                """
                {"revision":1,"ALL":1}""",
                """
                {"revision":1,"CPU":0}""",
                """
                {"revision":1,"CPU":"1"}""",
                """
                {"revision":1,"CPU":1,"CPU":2}""",
                """
                {"revision":1,"CPU":9007199254740992}""",
                """
                {"revision":1,"CPU":7}""",
                "[]", "", "{}")) {
            var response = check(body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("code").asText()).as(body).isEqualTo("INVALID_CONFIGURATION");
        }
    }

    @Test
    void theSameConfigurationStaysDeterministicAndNeverClaimsAFullPass() throws Exception {
        JsonNode first = json.readTree(check(COMPLETE).body()).path("data");
        JsonNode second = json.readTree(check(COMPLETE).body()).path("data");
        assertThat(first.path("checks")).isEqualTo(second.path("checks"));
        assertThat(first.path("corePassed").asBoolean()).isFalse();
        assertThat(first.path("unexecutedRules")).hasSize(5);
    }

    @Test
    void aValueWithNoVerifiedSourceBecomesUnknownInsteadOfPassing() throws Exception {
        long board = cloneProduct(3);
        try {
            // Drop the socket's provenance only: the value stays correct, so a naive engine would pass.
            db.update("""
                UPDATE product_specifications SET parameters=JSON_SET(parameters, CONCAT('$[', ?, '].sourceUrl'), '')
                WHERE product_id=?
                """, 0, board);
            JsonNode data = json.readTree(check(COMPLETE.replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board))
                    .body()).path("data");

            JsonNode c01 = rule(data, "C01");
            assertThat(c01.path("state").asText()).isEqualTo("UNKNOWN");
            assertThat(c01.path("message").asText()).contains("已核验");
            // The stored value is still reported honestly; it simply cannot decide a PASS.
            assertThat(c01.path("details").toString()).contains("LGA1700");
            // An unrelated rule with complete provenance still reports its own result.
            assertThat(rule(data, "C04").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("corePassed").asBoolean()).isFalse();
        } finally { deleteProduct(board); }
    }

    @Test
    void aMalformedCatalogValueIsReportedAsUnknownRatherThanAServerError() throws Exception {
        long board = cloneProduct(3);
        try {
            db.update("""
                UPDATE product_specifications SET parameters=JSON_SET(parameters, CONCAT('$[', ?, '].value'),
                    JSON_ARRAY(5, 7))
                WHERE product_id=?
                """, slotIndex(board, "memoryGenerations"), board);
            var response = check(COMPLETE.replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode data = json.readTree(response.body()).path("data");

            assertThat(rule(data, "C02").path("state").asText()).isEqualTo("UNKNOWN");
            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("corePassed").asBoolean()).isFalse();
        } finally { deleteProduct(board); }
    }

    @Test
    void malformedCpuSocketsReturnUnknownWithoutLosingOtherDiagnostics() throws Exception {
        long cpu = cloneProduct(1);
        try {
            for (Object value : List.of(List.of("LGA1700"), java.util.Map.of())) {
                setParameter(cpu, "socket", json.valueToTree(value));
                var response = check(COMPLETE.replace("\"CPU\":1", "\"CPU\":" + cpu));
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                JsonNode data = json.readTree(response.body()).path("data");
                assertThat(data.path("checks")).hasSize(5);
                assertThat(rule(data, "C01").path("state").asText()).isEqualTo("UNKNOWN");
                assertThat(rule(data, "C03").path("state").asText()).isEqualTo("UNKNOWN");
                assertThat(rule(data, "C04").path("state").asText()).isEqualTo("PASS");
                assertThat(data.path("corePassed").asBoolean()).isFalse();
            }
        } finally { deleteProduct(cpu); }
    }

    @Test
    void textualStickCountIsUnknownRatherThanAPassingZeroStickKit() throws Exception {
        long memory = cloneProduct(7);
        try {
            setParameter(memory, "stickCount", json.getNodeFactory().textNode("bad"));
            var response = check(COMPLETE.replace("\"MEMORY\":7", "\"MEMORY\":" + memory));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode data = json.readTree(response.body()).path("data");
            assertThat(rule(data, "C02").path("state").asText()).isEqualTo("UNKNOWN");
            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("corePassed").asBoolean()).isFalse();
        } finally { deleteProduct(memory); }
    }

    @Test
    void aKnownMemoryGenerationConflictFailsEvenWithUnknownStickCount() throws Exception {
        long memory = cloneProduct(7);
        try {
            setParameter(memory, "generation", json.getNodeFactory().textNode("DDR4"));
            setParameter(memory, "stickCount", json.nullNode());
            var response = check(COMPLETE.replace("\"MEMORY\":7", "\"MEMORY\":" + memory));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode data = json.readTree(response.body()).path("data");
            assertThat(rule(data, "C02").path("state").asText()).isEqualTo("FAIL");
            assertThat(rule(data, "C02").path("message").asText()).contains("DDR4", "DDR5");
            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("checks")).hasSize(5);
        } finally { deleteProduct(memory); }
    }

    @Test
    void aKnownSsdFormConflictFailsEvenWithUnknownProtocol() throws Exception {
        long ssd = cloneProduct(9);
        try {
            setParameter(ssd, "formFactor", json.getNodeFactory().textNode("2.5-inch"));
            setParameter(ssd, "interface", json.getNodeFactory().textNode("SATA"));
            setParameter(ssd, "protocol", json.nullNode());
            var response = check(COMPLETE.replace("\"SSD\":9", "\"SSD\":" + ssd));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode data = json.readTree(response.body()).path("data");
            assertThat(rule(data, "C05").path("state").asText()).isEqualTo("FAIL");
            assertThat(rule(data, "C05").path("message").asText()).contains("2.5-inch");
            assertThat(rule(data, "C01").path("state").asText()).isEqualTo("PASS");
            assertThat(data.path("checks")).hasSize(5);
        } finally { deleteProduct(ssd); }
    }

    private void setParameter(long product, String code, JsonNode value) throws Exception {
        JsonNode parameters = json.readTree(db.queryForObject(
                "SELECT parameters FROM product_specifications WHERE product_id=?", String.class, product));
        var parameter = (com.fasterxml.jackson.databind.node.ObjectNode) parameters.get(slotIndex(product, code));
        parameter.set("value", value);
        db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?", json.writeValueAsString(parameters), product);
    }

    private long cloneProduct(long source) {
        String sku = "t10_" + UUID.randomUUID();
        db.update("""
            INSERT INTO products(sku,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
            SELECT ?,name,category,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version
            FROM products WHERE id=?
            """, sku, source);
        long id = db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
        db.update("""
            INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on)
            SELECT ?,description,parameters,data_version,verified_on FROM product_specifications WHERE product_id=?
            """, id, source);
        return id;
    }

    private void deleteProduct(long id) {
        db.update("DELETE FROM product_specifications WHERE product_id=?", id);
        db.update("DELETE FROM products WHERE id=?", id);
    }

    private int slotIndex(long product, String code) throws Exception {
        var stored = db.queryForObject("SELECT parameters FROM product_specifications WHERE product_id=?", String.class, product);
        JsonNode parameters = json.readTree(stored);
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).path("code").asText().equals(code)) return index;
        }
        throw new AssertionError("fixture board has no " + code);
    }

    private static List<String> missing(JsonNode data) {
        var categories = new java.util.ArrayList<String>();
        for (JsonNode entry : data.path("missing")) categories.add(entry.path("category").asText());
        return categories;
    }

    private static JsonNode rule(JsonNode data, String code) {
        for (JsonNode check : data.path("checks")) if (check.path("rule").asText().equals(code)) return check;
        throw new AssertionError("missing rule " + code);
    }

    /** Anonymous POST that still honours the project's CSRF policy. */
    HttpResponse<String> check(String body) throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var csrf = browser.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        JsonNode token = json.readTree(csrf.body()).path("data");
        return browser.send(HttpRequest.newBuilder(uri("/api/compatibility/check"))
                .header("Content-Type", "application/json")
                .header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
