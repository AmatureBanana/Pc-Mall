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

@SpringBootTest(classes=MallApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceAdministrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t08_" + UUID.randomUUID().toString().substring(0,8);
    HttpClient admin;
    @BeforeAll void login() throws Exception {
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'ADMIN')",prefix,passwords.encode("Performance123!"),"评分测试管理员");
        admin=browser();
        assertThat(post(admin,"/api/auth/login",Map.of("username",prefix,"password","Performance123!")).statusCode()).isEqualTo(200);
    }
    @AfterAll void cleanup() {
        db.update("DELETE FROM admin_audit WHERE actor_username=?",prefix);
        db.update("DELETE s FROM product_performance s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?",prefix+"%");
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?",prefix+"%");
        db.update("DELETE FROM products WHERE sku LIKE ?",prefix+"%");
        db.update("DELETE FROM account WHERE username LIKE ?",prefix+"%");
    }
    @Test void administratorMaintainsRenderingReferenceAndPublicReadsCurrentAuditedScore() throws Exception {
        long id=product("happy"); String path="/api/admin/products/"+id;
        var missing=get(admin,path+"/performance");
        assertThat(missing.statusCode()).as(missing.body()).isEqualTo(200);
        assertThat(data(missing).path("performance").path("state").asText()).isEqualTo("MISSING");
        var input=reference(0); var saved=post(admin,path+"/performance",input);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(data(saved).path("revision").asInt()).isEqualTo(1);
        assertThat(data(saved).path("performance").path("score").asText()).isEqualTo("62.500000");
        var detail=data(get(browser(),"/api/products/"+id));
        assertThat(detail.path("performance").path("state").asText()).isEqualTo("VALID");
        assertThat(detail.path("performance").path("mappingVersion").asText()).isEqualTo("performance-v1");
        assertThat(detail.path("performance").path("score").asText()).isEqualTo("62.500000");
        var audit=data(get(admin,path+"/audit")).get(0);
        assertThat(audit.path("action").asText()).isEqualTo("PERFORMANCE");
        assertThat(audit.path("actorUsername").asText()).isEqualTo(prefix);
        assertThat(audit.path("afterValue").path("reference").path("rawScore").asText()).isEqualTo("325");
        input.put("revision",1); input.put("rawScore","125");
        assertThat(post(admin,path+"/performance",input).statusCode()).isEqualTo(200);
        assertThat(data(get(browser(),"/api/products/"+id)).path("performance").path("score").asText()).isEqualTo("37.500000");
        assertThat(post(admin,path+"/performance",input).statusCode()).isEqualTo(409);
    }
    @Test void amdCpuReferenceKeepsItsManufacturerPrefixAndExactVariant() throws Exception {
        long id=product("amd");
        db.update("UPDATE products SET brand='AMD',model='Ryzen 7 7800X3D' WHERE id=?",id);
        var input=reference(0); input.put("deviceName","AMD Ryzen 7 7800X3D");
        var saved=post(admin,"/api/admin/products/"+id+"/performance",input);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
    }
    @Test void missingInvalidAndChangedModelNeverProduceDefaultEffectiveScores() throws Exception {
        long id=product("invalid"); String path="/api/admin/products/"+id;
        var missing=data(get(browser(),"/api/products/"+id)).path("performance");
        assertThat(missing.path("state").asText()).isEqualTo("MISSING");
        assertThat(missing.has("score")).isTrue(); assertThat(missing.path("score").isNull()).isTrue();
        var input=reference(0); assertThat(post(admin,path+"/performance",input).statusCode()).isEqualTo(200);
        input.put("revision",1); input.put("valid",false); input.put("reason","发现样本条件不可靠");
        assertThat(post(admin,path+"/performance",input).statusCode()).isEqualTo(200);
        assertThat(data(get(browser(),"/api/products/"+id)).path("performance").path("state").asText()).isEqualTo("INVALID");
        assertThat(data(get(browser(),"/api/products/"+id)).path("performance").path("score").isNull()).isTrue();
        input.put("revision",2); input.put("valid",true); assertThat(post(admin,path+"/performance",input).statusCode()).isEqualTo(200);
        assertThat(post(admin,path+"/status",Map.of("revision",3,"status","OFF_SALE")).statusCode()).isEqualTo(200);
        assertThat(post(admin,path,Map.of("revision",4,"model","i5-14600KF","brand","Intel","parameters",List.of())).statusCode()).isEqualTo(200);
        assertThat(data(get(admin,path+"/performance")).path("performance").path("state").asText()).isEqualTo("STALE");
        assertThat(data(get(admin,path+"/performance")).path("performance").path("score").isNull()).isTrue();
    }
    @Test void mixedConditionsMalformedReferencesAndPolicyEditsAreRejected() throws Exception {
        long id=product("validation"); String path="/api/admin/products/"+id+"/performance";
        Map<String,Object> invalid=Map.ofEntries(Map.entry("rawScore","-1"),Map.entry("benchmarkVersion","4.4.0"),Map.entry("mappingVersion","custom"),
                Map.entry("deviceName","Intel Core i5-14600KF"),Map.entry("operatingSystem","Linux"),Map.entry("computeType","CUDA"),
                Map.entry("snapshotSha256","not-a-hash"),Map.entry("snapshotDate","2099-01-01"),Map.entry("sourceUrl","https://example.com/data"),
                Map.entry("scenes",List.of("monster")),Map.entry("sampleIds",List.of("bad")),Map.entry("sampleCount",4294967299L),
                Map.entry("sampleConditions",""),Map.entry("matchNote",""),Map.entry("reason",""));
        for (var change:invalid.entrySet()) { var input=reference(0); input.put(change.getKey(),change.getValue());
            assertThat(post(admin,path,input).statusCode()).as(change.getKey()).isEqualTo(400); }
        var forged=reference(0); forged.put("score",100); assertThat(post(admin,path,forged).statusCode()).isEqualTo(400);
        assertThat(data(get(admin,path)).path("revision").asInt()).isZero();
        assertThat(data(get(admin,path)).path("performance").path("state").asText()).isEqualTo("MISSING");
    }
    @Test void authorizationAndAuditFailureDoNotMutateReferencesOrRevisions() throws Exception {
        long id=product("rollback"); String path="/api/admin/products/"+id;
        assertThat(post(browser(),path+"/performance",reference(0)).statusCode()).isEqualTo(401);
        var user=browser(); String username=prefix+"u";
        post(user,"/api/auth/register",Map.of("username",username,"password","Performance123!","confirmPassword","Performance123!"));
        post(user,"/api/auth/login",Map.of("username",username,"password","Performance123!"));
        assertThat(get(user,path+"/performance").statusCode()).isEqualTo(403);
        assertThat(post(user,path+"/performance",reference(0)).statusCode()).isEqualTo(403);
        var noCsrf=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path+"/performance")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(reference(0)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        String constraint="fault_"+prefix;
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT "+constraint+" CHECK (actor_username <> '"+prefix+"' OR object_type <> 'PRODUCT' OR object_id <> "+id+" OR action <> 'PERFORMANCE')");
        try {
            assertThat(post(admin,path+"/performance",reference(0)).statusCode()).isEqualTo(503);
            assertThat(data(get(admin,path+"/performance")).path("performance").path("state").asText()).isEqualTo("MISSING");
            assertThat(data(get(admin,path)).path("revision").asInt()).isZero();
            assertThat(data(get(admin,path+"/audit")).size()).isEqualTo(1);
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK "+constraint); }
    }
    @Test void concurrentSameRevisionWritesHaveExactlyOneWinner() throws Exception {
        long id=product("race"); String path="/api/admin/products/"+id+"/performance";
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2); var gate=new java.util.concurrent.CyclicBarrier(2);
        try {
            var a=pool.submit(() -> { gate.await(); return post(admin,path,reference(0)).statusCode(); });
            var b=pool.submit(() -> { gate.await(); return post(admin,path,reference(0)).statusCode(); });
            assertThat(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(data(get(admin,path)).path("revision").asInt()).isEqualTo(1);
            assertThat(data(get(admin,"/api/admin/products/"+id+"/audit")).size()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void amdReferenceGpuUsesHipAndDoesNotConfuseBoardVariantWithDeviceName() throws Exception {
        String source="https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html";
        var created=post(admin,"/api/admin/products",Map.of("sku",prefix+"gpu","category","GPU","brand","AMD","model","Radeon RX 7800 XT (AMD reference)",
                "parameters",List.of(Map.of("code","boardVariant","value","AMD reference","unit","","sourceUrl",source),Map.of("code","vram","value",16,"unit","GB","sourceUrl",source))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long id=data(created).path("product").path("id").asLong();
        var input=reference(0); input.put("deviceName","AMD Radeon RX 7800 XT"); input.put("computeType","HIP");
        var saved=post(admin,"/api/admin/products/"+id+"/performance",input);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(data(saved).path("performance").path("score").asText()).isEqualTo("16.250000");
    }
    @Test void benchmarkCpuCoreSuffixMustAgreeWithTheVerifiedProductCoreCount() throws Exception {
        long id=product("amdAlias");
        db.update("UPDATE products SET brand='AMD',model='Ryzen 7 7800X3D' WHERE id=?",id);
        String parameters=json.writeValueAsString(List.of(Map.of("code","cores","value",8,"unit","核","sourceUrl","https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html")));
        db.update("UPDATE product_specifications SET parameters=? WHERE product_id=?",parameters,id);
        String path="/api/admin/products/"+id+"/performance";
        var input=reference(0); input.put("deviceName","AMD Ryzen 7 7800X3D 8-Core Processor");
        var saved=post(admin,path,input);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        input.put("revision",1); input.put("deviceName","AMD Ryzen 7 7800X3D 16-Core Processor");
        assertThat(post(admin,path,input).statusCode()).isEqualTo(400);
        input.put("deviceName","AMD Ryzen 7 7800X 8-Core Processor");
        assertThat(post(admin,path,input).statusCode()).isEqualTo(400);
    }
    @Test void revokedRenderingReferenceAlsoDisappearsFromListScoreTags() throws Exception {
        long id=product("tagRevoked"); String path="/api/admin/products/"+id+"/performance";
        var input=reference(0);
        assertThat(post(admin,path,input).statusCode()).isEqualTo(200);
        var current=data(get(browser(),"/api/products/"+id)).path("product").path("performanceTags");
        assertThat(current.get(0).path("kind").asText()).isEqualTo("SCORE");
        input.put("revision",1); input.put("valid",false); input.put("reason","撤销参考数据");
        assertThat(post(admin,path,input).statusCode()).isEqualTo(200);
        var items=data(get(browser(),"/api/products?keyword="+prefix+"tagRevoked"));
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("performanceTags").get(0).path("kind").asText()).isEqualTo("UNVERIFIED");
        assertThat(items.get(0).path("performanceTags").toString()).doesNotContain("62.5分");
    }
    Map<String,Object> reference(long revision) {
        var input=new LinkedHashMap<String,Object>();
        input.put("revision",revision); input.put("valid",true); input.put("rawScore","325");
        input.put("sourceUrl","https://opendata.blender.org/download/"); input.put("snapshotDate","2026-11-03");
        input.put("snapshotSha256","a".repeat(64)); input.put("benchmarkVersion","4.3.0"); input.put("mappingVersion","performance-v1");
        input.put("deviceName","Intel Core i5-14600K"); input.put("computeType","CPU"); input.put("operatingSystem","Windows");
        input.put("scenes",List.of("classroom","junkshop","monster")); input.put("sampleCount",3);
        input.put("sampleIds",List.of("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000002","00000000-0000-4000-8000-000000000003"));
        input.put("sampleConditions","仅测试夹具；固定版本、Windows、单CPU完整三场景后取中位数");
        input.put("matchNote","仅测试夹具：明确匹配i5-14600K，不匹配KF"); input.put("reason","测试评分维护");
        return input;
    }
    long product(String suffix) throws Exception {
        var response=post(admin,"/api/admin/products",Map.of("sku",prefix+suffix,"category","CPU","model","i5-14600K","brand","Intel"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id=data(response).path("product").path("id").asLong();
        // Setup only: isolated test SKU; assertions always use HTTP interfaces.
        db.update("UPDATE products SET status='ON_SALE' WHERE id=?",id);
        return id;
    }
    HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> get(HttpClient client,String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(HttpClient client,String path,Object input) throws Exception {
        var csrf=data(get(client,"/api/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
                .header(csrf.path("headerName").asText(),csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
}
