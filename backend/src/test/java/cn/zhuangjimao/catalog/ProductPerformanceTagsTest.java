package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes=MallApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductPerformanceTagsTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    final String prefix="t26_"+UUID.randomUUID().toString().substring(0,8);
    HttpClient admin;
    @BeforeAll void login() throws Exception {
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'ADMIN')",prefix,passwords.encode("ProductTags123!"),"标签测试管理员");
        admin=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        assertThat(post("/api/auth/login",Map.of("username",prefix,"password","ProductTags123!")).statusCode()).isEqualTo(200);
    }
    @AfterAll void cleanup() {
        db.update("DELETE FROM admin_audit WHERE actor_username=?",prefix);
        db.update("DELETE s FROM product_performance s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?",prefix+"%");
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?",prefix+"%");
        db.update("DELETE FROM products WHERE sku LIKE ?",prefix+"%");
        db.update("DELETE FROM account WHERE username=?",prefix);
    }
    @Test void allSaleProductsHaveCategorySpecificTagsAndListMatchesDetail() throws Exception {
        var items=get("/api/products");
        assertThat(items.size()).isEqualTo(13);
        for (var product:items) {
            var tags=product.path("performanceTags");
            assertThat(tags.isArray()).as(product.path("sku").asText()).isTrue();
            assertThat(tags.size()).isBetween(1,4);
            for (var tag:tags) {
                assertThat(tag.path("label").asText()).isNotBlank();
                assertThat(tag.path("kind").asText()).isIn("SCORE","CAPABILITY","UNVERIFIED");
                if (!tag.path("kind").asText().equals("UNVERIFIED")) assertThat(tag.path("sourceUrl").asText()).startsWith("https://");
            }
            var detail=get("/api/products/"+product.path("id").asLong());
            assertThat(detail.path("product")).isEqualTo(product);
            String category=product.path("category").asText();
            if (Set.of("MOTHERBOARD","PSU","CASE","COOLER").contains(category)) {
                for (var tag:tags) assertThat(tag.path("kind").asText()).isEqualTo("CAPABILITY");
            }
            if (category.equals("PSU")) assertThat(tags.toString()).contains("额定750W","ATX 3.1");
            if (category.equals("CASE")) assertThat(tags.toString()).contains("显卡限长370mm","散热限高163mm");
            if (category.equals("COOLER")) assertThat(tags.toString()).contains("高度155mm");
            if (category.equals("MEMORY")) assertThat(tags.toString()).contains("综合评分 47.3分","32GB");
            if (category.equals("SSD")) assertThat(tags.toString()).contains("综合评分 59.7分","1000GB");
        }
    }
    @Test void changedParametersUpdateEveryReadAndMissingParametersBecomeUnverified() throws Exception {
        var input=new LinkedHashMap<String,Object>();
        input.put("sku",prefix+"psu"); input.put("category","PSU"); input.put("name","标签测试电源"); input.put("brand","测试夹具"); input.put("model","fixture");
        input.put("summary","参数变更测试"); input.put("description","仅用于隔离测试库"); input.put("imageUrl","/images/psu.svg"); input.put("sourceUrl","https://example.com/psu"); input.put("price","100.50");
        var parameter=json.createObjectNode().put("code","ratedPower").put("value",650).put("unit","W").put("sourceUrl","https://example.com/psu");
        input.put("parameters",List.of(parameter));
        var created=post("/api/admin/products",input); assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long id=data(created).path("product").path("id").asLong(); String path="/api/admin/products/"+id;
        assertThat(post(path+"/status",Map.of("revision",0,"status","ON_SALE")).statusCode()).isEqualTo(200);
        assertThat(get("/api/products?keyword="+prefix).get(0).path("performanceTags").toString()).contains("额定650W");
        input.remove("sku"); input.remove("category"); input.remove("price"); input.put("revision",1); parameter.put("value",850);
        assertThat(post(path,input).statusCode()).isEqualTo(200);
        var listed=get("/api/products?keyword="+prefix).get(0);
        assertThat(listed.path("performanceTags").toString()).contains("额定850W").doesNotContain("650W");
        assertThat(get("/api/products/"+id).path("product").path("performanceTags")).isEqualTo(listed.path("performanceTags"));
        var adminDetail=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(data(adminDetail).path("performanceTags")).isEqualTo(listed.path("performanceTags"));
        assertThat(post(path+"/status",Map.of("revision",2,"status","OFF_SALE")).statusCode()).isEqualTo(200);
        input.put("revision",3); parameter.putNull("value");
        var missing=post(path,input); assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(data(missing).path("performanceTags").get(0).path("kind").asText()).isEqualTo("UNVERIFIED");
        assertThat(data(missing).path("performanceTags").toString()).doesNotContain("850W");
    }

    @Test void aSavedMemoryScoreRequiresTimingsFromTheRatedSpeedAndAllThreeValues() throws Exception {
        var seed=get("/api/products?category=MEMORY").get(0);
        var parameters=get("/api/products/"+seed.path("id").asLong()).path("parameters");
        var input=new LinkedHashMap<String,Object>(Map.of("name","内存评分测试","brand","测试夹具","model","fixture",
                "summary","时序档位测试","description","仅用于隔离测试库","imageUrl","/images/memory.svg",
                "sourceUrl","https://example.com/memory","parameters",parameters));
        input.put("sku",prefix+"memory"); input.put("category","MEMORY"); input.put("price","100.00");
        var created=post("/api/admin/products",input);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long id=data(created).path("product").path("id").asLong();
        String path="/api/admin/products/"+id;
        try {
            assertThat(post(path+"/status",Map.of("revision",0,"status","ON_SALE")).statusCode()).isEqualTo(200);
            assertThat(get("/api/products/"+id).path("performance").path("score").asText()).isEqualTo("47.252110");
            input.remove("sku"); input.remove("category"); input.remove("price"); input.put("revision",1);
            var rate=parameter(parameters,"dataRate"); rate.put("value",6200);
            var edited=post(path,input);
            assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
            var invalid=get("/api/products/"+id);
            assertThat(invalid.path("performance").path("state").asText()).isEqualTo("INVALID");
            assertThat(invalid.path("performance").path("score").isNull()).isTrue();
            assertThat(invalid.path("performance").path("note").asText()).contains("同一档位");
            assertThat(data(edited).path("performanceTags")).isEqualTo(invalid.path("product").path("performanceTags"));
            input.put("revision",2); rate.put("value",6000); parameter(parameters,"casLatency").putNull("value");
            assertThat(post(path,input).statusCode()).isEqualTo(200);
            var missing=get("/api/products/"+id).path("performance");
            assertThat(missing.path("state").asText()).isEqualTo("MISSING");
            assertThat(missing.path("score").isNull()).isTrue();

            input.put("revision",3); input.put("model","different-fixture"); parameter(parameters,"casLatency").put("value",36);
            var reused=post(path,input);
            assertThat(reused.statusCode()).as(reused.body()).isEqualTo(400);
            assertThat(reused.body()).contains("清空旧型号");
            var profile=Set.of("dataRate","timingDataRate","casLatency","trcd","trp");
            for (var code:profile) parameter(parameters,code).putNull("value");
            var replaced=post(path,input);
            assertThat(replaced.statusCode()).as(replaced.body()).isEqualTo(200);
            var changed=get("/api/products/"+id);
            assertThat(changed.path("performance").path("state").asText()).isEqualTo("MISSING");
            for (var field:changed.path("parameters")) assertThat(profile).doesNotContain(field.path("code").asText());
            input.put("revision",4);
            parameter(parameters,"dataRate").put("value",6000).put("sourceUrl","");
            parameter(parameters,"timingDataRate").put("value",6000).put("sourceUrl","");
            parameter(parameters,"casLatency").put("value",36).put("sourceUrl","");
            for (var code:List.of("trcd","trp")) parameter(parameters,code).put("value",38).put("sourceUrl","");
            assertThat(post(path,input).statusCode()).isEqualTo(400);
            assertThat(get("/api/products/"+id).path("performance").path("state").asText()).isEqualTo("MISSING");
        } finally {
            var current=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
            post(path+"/status",Map.of("revision",data(current).path("revision").asLong(),"status","OFF_SALE"));
        }
    }

    private ObjectNode parameter(JsonNode parameters,String code) {
        for (var entry:parameters) if (entry.path("code").asText().equals(code)) return (ObjectNode)entry;
        throw new AssertionError("Missing parameter: "+code);
    }
    HttpResponse<String> post(String path,Object input) throws Exception {
        var csrfResponse=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var csrf=data(csrfResponse);
        return admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
                .header(csrf.path("headerName").asText(),csrf.path("token").asText()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    JsonNode get(String path) throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
}
