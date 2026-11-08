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
    // (LGA1700 · Micro-ATX · DDR5 · M.2 PCIe/NVMe), 5 RTX4070 SUPER, 7 Kingston DDR5 2×16GB, 8 AK400,
    // 9 Samsung 990 PRO (M.2 2280 · PCIe · NVMe), 11 RM750e, 12 CC560 (ATX/M-ATX/Mini-ITX).
    private static final String COMPLETE = """
            {"revision":3,"GPU":5,"CPU":1,"MOTHERBOARD":3,"MEMORY":7,"SSD":9,"PSU":11,"COOLER":8,"CASE":12}""";

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;

    @Test
    void requestedMemoryModulesMustFitVerifiedBoardSlots() throws Exception {
        long board = cloneProduct(3);
        long memory = db.queryForObject("SELECT id FROM products WHERE sku='KINGSTON-KF560C36BBE2-16'", Long.class);
        try {
            String config = COMPLETE.replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board)
                    .replace("\"MEMORY\":7", "\"MEMORY\":" + memory);
            String four = config.replaceFirst("\\{", "{\"memoryQuantity\":4,");
            String two = config.replaceFirst("\\{", "{\"memoryQuantity\":2,");
            JsonNode pass = json.readTree(check(four).body()).path("data");
            assertThat(pass.path("memorySlots").asInt()).isEqualTo(4);
            assertThat(pass.path("corePassed").asBoolean()).isTrue();

            setParameter(board, "memorySlots", json.getNodeFactory().numberNode(2));
            JsonNode fail = json.readTree(check(four).body()).path("data");
            assertThat(fail.path("memorySlots").asInt()).isEqualTo(2);
            assertThat(rule(fail, "C02").path("state").asText()).isEqualTo("FAIL");
            assertThat(rule(fail, "C02").path("message").asText()).contains("4 条", "2 个内存插槽");
            assertThat(json.readTree(check(two).body()).path("data").path("corePassed").asBoolean()).isTrue();
            assertThat(check(four.replace("\"memoryQuantity\":4", "\"memoryQuantity\":5")).statusCode()).isEqualTo(400);
        } finally { deleteProduct(board); }
    }

    @Test
    void realCatalogPowerAndSpaceBoundariesAreReevaluatedOnEveryRequest() throws Exception {
        long psu = cloneProduct(11), box = cloneProduct(12);
        try {
            String config = COMPLETE.replace("\"PSU\":11", "\"PSU\":" + psu).replace("\"CASE\":12", "\"CASE\":" + box);
            setParameter(psu, "ratedPower", json.getNodeFactory().numberNode(626));
            setParameter(box, "maxGpuLength", json.getNodeFactory().numberNode(244));
            setParameter(box, "maxCoolerHeight", json.getNodeFactory().numberNode(155));
            JsonNode pass = json.readTree(check(config).body()).path("data");
            assertThat(pass.path("status").asText()).isEqualTo("CORE_PASSED");
            setParameter(psu, "ratedPower", json.getNodeFactory().numberNode(625));
            setParameter(box, "maxGpuLength", json.getNodeFactory().numberNode(243.99));
            setParameter(box, "maxCoolerHeight", json.getNodeFactory().numberNode(154.99));
            JsonNode fail = json.readTree(check(config).body()).path("data");
            for (String code : List.of("C08", "C09", "C10")) assertThat(rule(fail, code).path("state").asText()).as(code).isEqualTo("FAIL");
            assertThat(fail.path("catalogVersion")).isNotEqualTo(pass.path("catalogVersion"));
            assertThat(fail.path("checks")).hasSize(9);
        } finally { deleteProduct(psu); deleteProduct(box); }
    }

    @Test
    void aSelectedMissingGpuIsPendingRatherThanNotApplicableOrZeroPower() throws Exception {
        String config = COMPLETE.replace("\"GPU\":5", "\"GPU\":9007199254740991");
        JsonNode data = json.readTree(check(config).body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(missing(data)).containsExactly("GPU");
        for (String code : List.of("C06", "C08", "C09")) assertThat(rule(data, code).path("state").asText()).as(code).isEqualTo("PENDING");
        long cpu = db.queryForObject("SELECT id FROM products WHERE sku='INTEL-I5-14600K'", Long.class);
        JsonNode integrated = json.readTree(check(config.replace("\"CPU\":1", "\"CPU\":" + cpu)).body()).path("data");
        assertThat(integrated.path("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(integrated.path("corePassed").asBoolean()).isFalse();
    }

    @Test
    void integratedBuildPassesWithoutGpuButAnIndependentCoolerIsStillRequired() throws Exception {
        long cpu = db.queryForObject("SELECT id FROM products WHERE sku='INTEL-I5-14600K'", Long.class);
        String integrated = COMPLETE.replace("\"GPU\":5,", "\"integratedGraphicsCpuId\":" + cpu + ",").replace("\"CPU\":1", "\"CPU\":" + cpu);
        JsonNode data = json.readTree(check(integrated).body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("CORE_PASSED");
        assertThat(data.path("missing")).isEmpty();
        assertThat(rule(data, "C06").path("state").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(rule(data, "C09").path("state").asText()).isEqualTo("NOT_APPLICABLE");
        data = json.readTree(check(integrated.replace(",\"COOLER\":8", "")).body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(missing(data)).containsExactly("COOLER");
        assertThat(data.path("corePassed").asBoolean()).isFalse();
    }

    @Test
    void missingGraphicsIsIncompleteWithoutAnOutputConflict() throws Exception {
        JsonNode data = json.readTree(check(COMPLETE.replace("\"GPU\":5,", "").replace(",\"SSD\":9", "")).body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(data.path("complete").asBoolean()).isFalse();
        assertThat(missing(data)).containsExactly("SSD", "GPU");
        assertThat(rule(data, "C05").path("state").asText()).isEqualTo("PENDING");
        assertThat(data.path("checks")).hasSize(9);
    }

    @Test
    void unknownPowerNeverPassesAndParameterChangesInvalidateTheSameRevision() throws Exception {
        long cpu = cloneProduct(1);
        try {
            String config = COMPLETE.replace("\"CPU\":1", "\"CPU\":" + cpu);
            JsonNode before = json.readTree(check(config).body()).path("data");
            setParameter(cpu, "peakPowerEstimate", json.nullNode());
            JsonNode after = json.readTree(check(config).body()).path("data");
            assertThat(after.path("status").asText()).isEqualTo("UNVERIFIED");
            assertThat(after.path("corePassed").asBoolean()).isFalse();
            assertThat(rule(after, "C08").path("state").asText()).isEqualTo("UNKNOWN");
            assertThat(after.path("catalogVersion")).isNotEqualTo(before.path("catalogVersion"));
            JsonNode missing = json.readTree(check(config.replace(",\"SSD\":9", "")).body()).path("data");
            assertThat(missing.path("status").asText()).isEqualTo("INCOMPLETE");
            assertThat(rule(missing, "C08").path("state").asText()).isEqualTo("UNKNOWN");
        } finally { deleteProduct(cpu); }
    }

    @Test
    void coreAdmissionDoesNotAssertSaleOrStockAndCoreRulesCannotBeDisabledByInput() throws Exception {
        long gpu = cloneProduct(5);
        try {
            db.update("UPDATE products SET stock=0,status='OFF_SALE' WHERE id=?", gpu);
            JsonNode data = json.readTree(check(COMPLETE.replace("\"GPU\":5", "\"GPU\":" + gpu)).body()).path("data");
            assertThat(data.path("corePassed").asBoolean()).isTrue();
            for (String field : List.of("disabledRules", "enabledRules", "ruleVersion")) {
                assertThat(check(COMPLETE.replace("}", ",\"" + field + "\":[]}")).statusCode()).isEqualTo(400);
            }
        } finally { deleteProduct(gpu); }
    }

    @Test
    void completeDiscreteBuildGetsTheOnlyCoreAdmissionAndVersionIdentity() throws Exception {
        JsonNode data = json.readTree(check(COMPLETE).body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("CORE_PASSED");
        assertThat(data.path("corePassed").asBoolean()).isTrue();
        assertThat(data.path("complete").asBoolean()).isTrue();
        assertThat(data.path("catalogVersion").asText()).hasSize(64);
        assertThat(data.path("checks")).hasSize(9);
        assertThat(data.path("unexecutedRules")).isEmpty();
        assertThat(data.path("unexecutedExtensions")).hasSize(6);
    }

    @Test
    void guestReadsCurrentDatabaseParametersAndSeesEachFinishedRuleApart() throws Exception {
        var response = check(COMPLETE);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data.path("revision").asInt()).isEqualTo(3);
        assertThat(data.path("ruleVersion").asText()).isNotBlank();
        assertThat(data.path("checkedAt").asText()).isNotBlank();
        // All nine actual checks passed for the complete discrete build.
        assertThat(data.path("corePassed").asBoolean()).isTrue();
        assertThat(data.path("checks").findValuesAsText("rule")).containsExactly("C01", "C02", "C03", "C04", "C05", "C06", "C08", "C09", "C10");
        for (String rule : List.of("C01", "C02", "C03", "C04", "C05")) {
            assertThat(rule(data, rule).path("state").asText()).as(rule).isEqualTo("PASS");
            assertThat(rule(data, rule).path("message").asText()).as(rule).isNotBlank();
            assertThat(rule(data, rule).path("categories").isArray()).as(rule).isTrue();
        }
        assertThat(data.path("unexecutedRules")).isEmpty();
        // Every selected sales unit is present; completeness is separate from sale and stock.
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
            assertThat(data.path("checks")).hasSize(9);
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
        assertThat(missing(partial)).containsExactly("MOTHERBOARD", "MEMORY", "SSD", "PSU", "CASE", "COOLER", "GPU");
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
    void theSameConfigurationStaysDeterministicWithTheSameCatalogIdentity() throws Exception {
        JsonNode first = json.readTree(check(COMPLETE).body()).path("data");
        JsonNode second = json.readTree(check(COMPLETE).body()).path("data");
        assertThat(first.path("checks")).isEqualTo(second.path("checks"));
        assertThat(first.path("corePassed").asBoolean()).isTrue();
        assertThat(first.path("catalogVersion")).isEqualTo(second.path("catalogVersion"));
        assertThat(first.path("unexecutedRules")).isEmpty();
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
                assertThat(data.path("checks")).hasSize(9);
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
            assertThat(data.path("checks")).hasSize(9);
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
            assertThat(data.path("checks")).hasSize(9);
        } finally { deleteProduct(ssd); }
    }

    @Test
    void anExplicitGraphicsChoiceIsRequiredWithoutCheckingTheDisplayOutputPath() throws Exception {
        long board = cloneProduct(3);
        long cpu = db.queryForObject("SELECT id FROM products WHERE sku='INTEL-I5-14600K'", Long.class);
        try {
            setParameter(board, "displayOutputs", json.createArrayNode());
            String config = COMPLETE.replace("\"GPU\":5,", "")
                    .replace("\"MOTHERBOARD\":3", "\"MOTHERBOARD\":" + board);
            for (long cpuId : List.of(1L, cpu)) {
                String current = config.replace("\"CPU\":1", "\"CPU\":" + cpuId);
                JsonNode data = json.readTree(check(current).body()).path("data");
                assertThat(data.path("status").asText()).isEqualTo("INCOMPLETE");
                assertThat(data.path("corePassed").asBoolean()).isFalse();
                assertThat(missing(data)).containsExactly("GPU");
                assertThat(data.path("checks").findValuesAsText("rule")).doesNotContain("C07").hasSize(9);
                JsonNode chosen = json.readTree(check(current.replace("}", ",\"integratedGraphicsCpuId\":" + cpuId + "}")).body()).path("data");
                assertThat(chosen.path("corePassed").asBoolean()).isEqualTo(cpuId == cpu);
            }
            assertThat(check(COMPLETE.replace("}", ",\"integratedGraphicsCpuId\":" + cpu + "}")).statusCode()).isEqualTo(400);
            assertThat(check(config.replace("}", ",\"integratedGraphicsCpuId\":" + cpu + "}")).statusCode()).isEqualTo(400);
        } finally { deleteProduct(board); }
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
