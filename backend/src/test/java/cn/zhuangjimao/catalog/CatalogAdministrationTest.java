package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** F07 behavior at authenticated HTTP boundaries, using the guarded real MySQL schema. */
@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogAdministrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t05_" + UUID.randomUUID().toString().substring(0, 8);
    HttpClient admin;

    @BeforeAll void login() throws Exception {
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'ADMIN')",
                prefix, passwords.encode("CatalogPassword123!"), "商品管理员");
        admin = browser();
        assertThat(post(admin, "/api/auth/login", Map.of("username", prefix, "password", "CatalogPassword123!")).statusCode()).isEqualTo(200);
    }
    @AfterAll void cleanup() {
        db.update("DELETE FROM admin_audit WHERE actor_username=?", prefix);
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?", prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?", prefix + "%");
        db.update("DELETE FROM account WHERE username=?", prefix);
    }

    @Test void administratorCreatesEightDraftCategoriesWithStableIdentityAndPagedFilters() throws Exception {
        assertThat(get(browser(), "/api/admin/products").statusCode()).isEqualTo(401);
        var user = browser();
        String username = prefix + "u";
        post(user, "/api/auth/register", Map.of("username", username, "password", "CatalogPassword123!", "confirmPassword", "CatalogPassword123!"));
        post(user, "/api/auth/login", Map.of("username", username, "password", "CatalogPassword123!"));
        try {
            assertThat(get(user, "/api/admin/products").statusCode()).isEqualTo(403);
            assertThat(post(user, "/api/admin/products", Map.of("sku", prefix, "category", "CPU")).statusCode()).isEqualTo(403);
            assertThat(post(user, "/api/admin/products/1", Map.of("revision",0)).statusCode()).isEqualTo(403);
            assertThat(post(user, "/api/admin/products/1/status", Map.of("revision",0,"status","OFF_SALE")).statusCode()).isEqualTo(403);
            assertThat(get(user,"/api/admin/products/1/audit").statusCode()).isEqualTo(403);
        } finally { db.update("DELETE FROM account WHERE username=?", username); }
        var ids = new HashSet<Long>();
        for (var category : HardwareCategory.values()) {
            var input = Map.of("sku", prefix + "cat" + category, "category", category.name(), "name", "商品草稿");
            var response = post(admin, "/api/admin/products", input);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
            var detail = data(response);
            long id = detail.path("product").path("id").asLong();
            assertThat(ids.add(id)).isTrue();
            assertThat(detail.path("product").path("status").asText()).isEqualTo("OFF_SALE");
            assertThat(data(get(admin, "/api/admin/products/" + id)).path("product").path("sku").asText()).isEqualTo(prefix + "cat" + category);
            assertThat(post(admin, "/api/admin/products", input).statusCode()).isEqualTo(409);
        }
        var page = data(get(admin, "/api/admin/products?keyword=" + prefix + "cat&status=OFF_SALE&page=2&size=3"));
        assertThat(page.path("total").asInt()).isEqualTo(8);
        assertThat(page.path("items").size()).isEqualTo(3);
        assertThat(data(get(admin, "/api/admin/products?keyword=" + prefix + "cat&category=CPU")).path("total").asInt()).isEqualTo(1);
        for (String query : List.of("page=0", "size=101", "status=DELETED", "category=PC")) {
            assertThat(get(admin, "/api/admin/products?" + query).statusCode()).isEqualTo(400);
        }
        var noCsrf = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/products"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"sku\":\"invalid\",\"category\":\"CPU\"}")).build();
        assertThat(admin.send(noCsrf,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test void editingPublishingAndOffSaleChangesAreValidatedAuditedAndVersioned() throws Exception {
        var input = json.createObjectNode().put("sku", prefix + "cycle").put("category", "CPU").put("price", "1999.50")
                .put("name", "测试CPU").put("brand", "Intel").put("model", "Test CPU").put("summary", "测试摘要")
                .put("description", "测试描述").put("imageUrl", "/images/intel-cpu.svg").put("sourceUrl", "https://example.com/cpu");
        var draft = data(post(admin, "/api/admin/products", input));
        long id = draft.path("product").path("id").asLong();
        String path = "/api/admin/products/" + id;
        var publish = post(admin, path + "/status", Map.of("status", "ON_SALE", "revision", 0));
        assertThat(publish.statusCode()).isEqualTo(400);
        assertThat(publish.body()).contains("socket", "peakPowerEstimate", "integratedGraphics");
        var edit = input.deepCopy(); edit.remove(List.of("sku", "category", "price"));
        edit.put("revision", 0).put("name", "新CPU名称");
        edit.set("parameters", json.readTree("""
            [{"code":"socket","value":"LGA1700","unit":"","sourceUrl":"https://example.com/cpu"},
             {"code":"integratedGraphics","value":false,"unit":"","sourceUrl":"https://example.com/cpu"},
             {"code":"peakPowerEstimate","value":181,"unit":"W","sourceUrl":"https://example.com/cpu"}]
            """));
        assertThat(post(admin, path, edit).statusCode()).isEqualTo(200);
        assertThat(post(admin, path, edit).statusCode()).isEqualTo(409);
        assertThat(post(admin, path + "/status", Map.of("status", "ON_SALE", "revision", 1)).statusCode()).isEqualTo(200);
        var publicDetail = data(get(browser(), "/api/products/" + id));
        assertThat(publicDetail.path("product").path("name").asText()).isEqualTo("新CPU名称");
        assertThat(publicDetail.path("product").path("price").asText()).isEqualTo("1999.50");
        edit.put("revision", 2); edit.set("parameters", json.createArrayNode());
        assertThat(post(admin, path, edit).statusCode()).isEqualTo(400);
        for (String protectedField : List.of("sku", "category", "price", "stock", "status", "id")) {
            var illegal = edit.deepCopy().put(protectedField, "tamper");
            assertThat(post(admin, path, illegal).statusCode()).as(protectedField).isEqualTo(400);
        }
        assertThat(post(admin, path + "/status", Map.of("status", "OFF_SALE", "revision", 2)).statusCode()).isEqualTo(200);
        assertThat(get(browser(), "/api/products/" + id).statusCode()).isEqualTo(409);
        assertThat(data(get(admin, path)).path("product").path("sku").asText()).isEqualTo(prefix + "cycle");
        var audit = data(get(admin, path + "/audit"));
        assertThat(audit.size()).isEqualTo(4);
        assertThat(audit.get(0).path("actorUsername").asText()).isEqualTo(prefix);
        assertThat(audit.get(0).path("actorId").asLong()).isPositive();
        assertThat(audit.get(0).path("occurredAt").asText()).isNotBlank();
        assertThat(audit.get(0).path("beforeValue").path("product").path("status").asText()).isEqualTo("ON_SALE");
        assertThat(audit.get(0).path("afterValue").path("product").path("status").asText()).isEqualTo("OFF_SALE");
    }

    @Test void eightCategoryParametersRejectBadTypesUnitsSetsAndMissingApplicableFields() throws Exception {
        Map<String,String> fixtures = Map.of(
                "CPU", "[\"socket\",\"integratedGraphics\",\"peakPowerEstimate\"]",
                "MOTHERBOARD", "[\"socket\",\"memoryGenerations\",\"memorySlots\",\"formFactor\",\"ssdSlots\",\"gpuInterfaces\",\"displayOutputs\"]",
                "GPU", "[\"installationInterface\",\"boardPowerEstimate\",\"length\",\"displayOutputs\"]",
                "MEMORY", "[\"generation\",\"totalCapacity\",\"stickCount\"]",
                "SSD", "[\"capacity\",\"originalCapacity\",\"formFactor\",\"interface\",\"protocol\"]",
                "PSU", "[\"ratedPower\"]", "CASE", "[\"supportedMotherboardFormFactors\",\"maxGpuLength\",\"maxCoolerHeight\"]",
                "COOLER", "[\"supportedSockets\",\"height\"]");
        for (var fixture : fixtures.entrySet()) {
            var schema = data(get(admin,"/api/admin/products/schema?category=" + fixture.getKey()));
            var codes = new ArrayList<String>(); schema.forEach(field -> codes.add(field.path("code").asText()));
            for (var required : json.readTree(fixture.getValue())) assertThat(codes).contains(required.asText());
            var draft = data(post(admin,"/api/admin/products",Map.of("sku",prefix + "schema" + fixture.getKey(),"category",fixture.getKey(),"price","12.50")));
            String path = "/api/admin/products/" + draft.path("product").path("id").asLong();
            var rejected = post(admin,path + "/status",Map.of("revision",0,"status","ON_SALE"));
            assertThat(rejected.statusCode()).isEqualTo(400);
            for (var required : json.readTree(fixture.getValue())) assertThat(rejected.body()).contains(required.asText());
        }
        var cpu = data(post(admin,"/api/admin/products",Map.of("sku",prefix + "invalid","category","CPU")));
        String path = "/api/admin/products/" + cpu.path("product").path("id").asLong();
        for (String invalid : List.of(
                "[{\"code\":\"socket\",\"value\":\"not-a-socket\",\"unit\":\"\"}]",
                "[{\"code\":\"integratedGraphics\",\"value\":\"false\",\"unit\":\"\"}]",
                "[{\"code\":\"peakPowerEstimate\",\"value\":181,\"unit\":\"kW\"}]",
                "[{\"code\":\"peakPowerEstimate\",\"value\":-1,\"unit\":\"W\"}]",
                "[{\"code\":\"memoryGenerations\",\"value\":[\"DDR5\",\"DDR5\"],\"unit\":\"\"}]",
                "[{\"code\":\"socket\",\"value\":\"AM5\",\"unit\":\"\"},{\"code\":\"socket\",\"value\":\"AM4\",\"unit\":\"\"}]",
                "[{\"code\":\"ratedPower\",\"value\":750,\"unit\":\"W\"}]")) {
            var result = post(admin,path,Map.of("revision",0,"parameters",json.readTree(invalid)));
            assertThat(result.statusCode()).as(result.body()).isEqualTo(400);
        }
        assertThat(post(admin,path,Map.of("revision",0,"imageUrl","javascript:alert(1)","parameters",List.of())).statusCode()).isEqualTo(400);
        assertThat(data(get(admin,path)).path("revision").asInt()).isZero();
    }

    @Test void auditStorageFailureRollsBackCreationEditsAndStatusAndConcurrentEditorsCannotOverwrite() throws Exception {
        var initial = fullProduct(prefix + "atomic","PSU");
        initial.put("name","原始名称");
        initial.set("parameters",json.valueToTree(List.of(parameter("ratedPower",750,"W"))));
        var draft = data(post(admin,"/api/admin/products",initial));
        long id = draft.path("product").path("id").asLong();
        String path = "/api/admin/products/" + id;
        int originalAudits = data(get(admin,path + "/audit")).size();
        String trigger = "fault_" + prefix;
        long maximumId = db.queryForObject("SELECT MAX(object_id) FROM admin_audit",Long.class);
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT " + trigger + " CHECK (actor_username <> '" + prefix
                + "' OR (object_id <= " + maximumId + " AND (object_id <> " + id + " OR action='CREATE')))");
        try {
            assertThat(post(admin,path + "/status",Map.of("revision",0,"status","ON_SALE")).statusCode()).isEqualTo(503);
            assertThat(data(get(admin,path)).path("product").path("status").asText()).isEqualTo("OFF_SALE");
            assertThat(post(admin,path,Map.of("revision",0,"name","错误变更","parameters",List.of())).statusCode()).isEqualTo(503);
            assertThat(post(admin,"/api/admin/products",Map.of("sku",prefix + "rollback","category","PSU")).statusCode()).isEqualTo(503);
            assertThat(data(get(admin,"/api/admin/products?keyword=" + prefix + "rollback")).path("total").asInt()).isZero();
            assertThat(data(get(admin,path)).path("product").path("name").asText()).isEqualTo("原始名称");
            assertThat(data(get(admin,path)).path("revision").asInt()).isZero();
            assertThat(data(get(admin,path)).path("parameters").get(0).path("value").asInt()).isEqualTo(750);
            assertThat(data(get(admin,path + "/audit")).size()).isEqualTo(originalAudits);
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK " + trigger); }
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (String name : List.of("并发A","并发B")) futures.add(executor.submit(() -> {
                start.await(); return post(admin,path,Map.of("revision",0,"name",name,"parameters",List.of())).statusCode();
            }));
            assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder(200,409);
            assertThat(data(get(admin,path)).path("revision").asInt()).isEqualTo(1);
            assertThat(data(get(admin,path + "/audit")).size()).isEqualTo(originalAudits + 1);
        } finally { executor.shutdownNow(); }
    }

    @Test void eachCategoryCanBePublishedWithTypedParametersAndCapacityConversionsAreExact() throws Exception {
        var fixtures = Map.of(
                "CPU",List.of(parameter("socket","AM5",""),parameter("integratedGraphics",true,""),parameter("peakPowerEstimate",160,"W")),
                "MOTHERBOARD",List.of(parameter("socket","AM5",""),parameter("formFactor","Micro-ATX",""),parameter("memoryGenerations",List.of("DDR5"),""),
                        parameter("memorySlots",4,"个"),parameter("displayOutputs",List.of("HDMI"),""),parameter("gpuInterfaces",List.of("PCIe x16"),""),
                        parameter("ssdSlots",List.of(Map.of("formFactor","M.2 2280","interface","PCIe","protocol","NVMe")),"")),
                "GPU",List.of(parameter("installationInterface","PCIe x16",""),parameter("boardPowerEstimate",220,"W"),parameter("length",244,"mm"),parameter("displayOutputs",List.of("DisplayPort"),"")),
                "MEMORY",List.of(parameter("generation","DDR5",""),parameter("stickCount",2,"条"),parameter("totalCapacity",32,"GB"),parameter("capacityPerStick",16,"GB")),
                "SSD",List.of(parameter("capacity",new java.math.BigDecimal("1099.511627776"),"GB"),parameter("originalCapacity",1,"TiB"),parameter("formFactor","M.2 2280",""),parameter("interface","PCIe",""),parameter("protocol","NVMe","")),
                "PSU",List.of(parameter("ratedPower",750,"W")),
                "CASE",List.of(parameter("supportedMotherboardFormFactors",List.of("ATX","Micro-ATX"),""),parameter("maxGpuLength",370,"mm"),parameter("maxCoolerHeight",163,"mm")),
                "COOLER",List.of(parameter("supportedSockets",List.of("AM5","AM4"),""),parameter("height",155,"mm")));
        for (var fixture : fixtures.entrySet()) {
            var input = fullProduct(prefix + "valid" + fixture.getKey(),fixture.getKey());
            input.set("parameters",json.valueToTree(fixture.getValue()));
            var created = post(admin,"/api/admin/products",input);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            long id = data(created).path("product").path("id").asLong();
            var published = post(admin,"/api/admin/products/" + id + "/status",Map.of("revision",0,"status","ON_SALE"));
            assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
            assertThat(get(browser(),"/api/products/" + id).statusCode()).isEqualTo(200);
            if (fixture.getKey().equals("SSD")) {
                input.remove(List.of("sku","category","price")); input.put("revision",1);
                ((com.fasterxml.jackson.databind.node.ObjectNode)input.path("parameters").get(0)).put("value",1024);
                assertThat(post(admin,"/api/admin/products/" + id,input).statusCode()).isEqualTo(400);
            }
            if (fixture.getKey().equals("MEMORY")) {
                input.remove(List.of("sku","category","price")); input.put("revision",1);
                ((com.fasterxml.jackson.databind.node.ObjectNode)input.path("parameters").get(2)).put("value",64);
                assertThat(post(admin,"/api/admin/products/" + id,input).statusCode()).isEqualTo(400);
            }
            assertThat(post(admin,"/api/admin/products/" + id + "/status",Map.of("revision",1,"status","OFF_SALE")).statusCode()).isEqualTo(200);
        }
    }

    @Test void existingIntelProductRemainsEditableWithItsVerifiedCoreAndThreadUnits() throws Exception {
        var matches = data(get(admin,"/api/admin/products?keyword=INTEL-I5-14600KF")).path("items");
        long id = matches.get(0).path("id").asLong();
        String path = "/api/admin/products/" + id;
        var original = data(get(admin,path));
        var verifiedOn = db.queryForObject("SELECT verified_on FROM product_specifications WHERE product_id=?",java.sql.Date.class,id);
        var edit = json.createObjectNode();
        for (String field : List.of("name","brand","model","summary","imageUrl","sourceUrl","priceNote")) edit.set(field,original.path("product").path(field));
        edit.set("parameters",original.path("parameters")); edit.set("description",original.path("description"));
        edit.set("revision",original.path("revision")); edit.put("name","可维护的既有Intel CPU");
        try {
            var saved = post(admin,path,edit);
            assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
            assertThat(data(get(browser(),"/api/products/" + id)).path("product").path("name").asText()).isEqualTo("可维护的既有Intel CPU");
        } finally {
            // Restore only this exact seeded fixture after public-interface assertions.
            db.update("UPDATE products SET name=?,revision=? WHERE id=?",original.path("product").path("name").asText(),original.path("revision").asLong(),id);
            db.update("UPDATE product_specifications SET description=?,parameters=?,data_version=?,verified_on=? WHERE product_id=?",
                    original.path("description").asText(),json.writeValueAsString(original.path("parameters")),original.path("dataVersion").asText(),verifiedOn,id);
        }
    }

    com.fasterxml.jackson.databind.node.ObjectNode fullProduct(String sku,String category) {
        return json.createObjectNode().put("sku",sku).put("category",category).put("name","测试商品").put("brand","测试品牌")
                .put("model","准确型号").put("summary","测试摘要").put("description","测试描述").put("imageUrl","/images/psu.svg")
                .put("sourceUrl","https://example.com/product").put("price","100.50");
    }
    Map<String,Object> parameter(String code,Object value,String unit) {
        return Map.of("code",code,"value",value,"unit",unit,"sourceUrl","https://example.com/spec");
    }
    HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }
    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception {
        var csrf = data(get(client, "/api/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
}
