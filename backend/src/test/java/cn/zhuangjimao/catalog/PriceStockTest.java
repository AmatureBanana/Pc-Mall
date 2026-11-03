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

@SpringBootTest(classes = MallApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PriceStockTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ProductInventory inventory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    final ObjectMapper json = new ObjectMapper();
    final String prefix = "t07_" + UUID.randomUUID().toString().substring(0, 8);
    HttpClient admin;

    @BeforeAll void login() throws Exception {
        db.update("INSERT INTO account(username,password_hash,nickname,role) VALUES (?,?,?,'ADMIN')",
                prefix, passwords.encode("PricePassword123!"), "价格库存管理员");
        admin = browser();
        assertThat(post(admin,"/api/auth/login",Map.of("username",prefix,"password","PricePassword123!")).statusCode()).isEqualTo(200);
    }
    @AfterAll void cleanup() {
        db.update("DELETE FROM admin_audit WHERE actor_username=?",prefix);
        db.update("DELETE FROM stock_reservation_items WHERE reference LIKE ?",prefix + "%");
        db.update("DELETE FROM stock_reservations WHERE reference LIKE ?",prefix + "%");
        db.update("DELETE s FROM product_specifications s JOIN products p ON p.id=s.product_id WHERE p.sku LIKE ?",prefix + "%");
        db.update("DELETE FROM products WHERE sku LIKE ?",prefix + "%");
        db.update("DELETE FROM account WHERE username=?",prefix);
    }
    @Test void priceChangeIsVisiblePubliclyVersionedAndAuditedWithReason() throws Exception {
        long id = product("price");
        String path = "/api/admin/products/" + id;
        var updated = post(admin,path + "/price",Map.of("revision",0,"price","321.09","reason","供应商报价更新"));
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(data(updated).path("revision").asLong()).isEqualTo(1);
        assertThat(data(get(admin,path)).path("product").path("price").asText()).isEqualTo("321.09");
        assertThat(data(get(browser(),"/api/products/" + id)).path("product").path("price").asText()).isEqualTo("321.09");
        var entry = data(get(admin,path + "/audit")).get(0);
        assertThat(entry.path("action").asText()).isEqualTo("PRICE");
        assertThat(entry.path("actorUsername").asText()).isEqualTo(prefix);
        assertThat(entry.path("afterValue").path("reason").asText()).isEqualTo("供应商报价更新");
        assertThat(entry.path("beforeValue").path("price").asText()).isEqualTo("100.50");
        assertThat(entry.path("afterValue").path("price").asText()).isEqualTo("321.09");
        assertThat(post(admin,path + "/price",Map.of("revision",0,"price","99","reason","旧页面变价")).statusCode()).isEqualTo(409);
    }
    @Test void concurrentDeltasAccumulateAndCannotMakeAvailableStockNegative() throws Exception {
        long id = product("stock");
        String path = "/api/admin/products/" + id;
        assertThat(post(admin,path + "/stock",Map.of("delta",5,"reason","首次入库")).statusCode()).isEqualTo(200);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try {
            var a = executor.submit(() -> { start.await(); return post(admin,path + "/stock",Map.of("delta",3,"reason","到货A")).statusCode(); });
            var b = executor.submit(() -> { start.await(); return post(admin,path + "/stock",Map.of("delta",4,"reason","到货B")).statusCode(); });
            assertThat(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsOnly(200);
            assertThat(data(get(browser(),"/api/products/" + id)).path("product").path("stock").asInt()).isEqualTo(12);
            var c = executor.submit(() -> { start.await(); return post(admin,path + "/stock",Map.of("delta",-8,"reason","盘亏A")).statusCode(); });
            var d = executor.submit(() -> { start.await(); return post(admin,path + "/stock",Map.of("delta",-8,"reason","盘亏B")).statusCode(); });
            assertThat(List.of(c.get(15,java.util.concurrent.TimeUnit.SECONDS),d.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { executor.shutdownNow(); }
        assertThat(data(get(admin,path)).path("product").path("stock").asInt()).isEqualTo(4);
        assertThat(data(get(admin,path)).path("revision").asInt()).isEqualTo(4);
        var audit = data(get(admin,path + "/audit")).get(0);
        assertThat(audit.path("action").asText()).isEqualTo("STOCK");
        assertThat(audit.path("beforeValue").path("stock").asInt()).isEqualTo(12);
        assertThat(audit.path("afterValue").path("stock").asInt()).isEqualTo(4);
        assertThat(audit.path("afterValue").path("reason").asText()).startsWith("盘亏");
    }
    @Test void reservationSurvivesAdministrationAndReleaseIsAtomicAndOnlyOnce() throws Exception {
        long a = product("reserveA"), b = product("reserveB");
        String path = "/api/admin/products/" + a;
        post(admin,path + "/stock",Map.of("delta",10,"reason","入库"));
        post(admin,"/api/admin/products/" + b + "/stock",Map.of("delta",5,"reason","入库"));
        String reference = prefix + "order";
        inventory.reserve(reference,Map.of(b,2,a,3));
        inventory.reserve(reference,Map.of(a,3,b,2));
        assertThat(data(get(admin,path)).path("product").path("stock").asInt()).isEqualTo(7);
        assertThat(data(get(admin,path)).path("reservedStock").asInt()).isEqualTo(3);
        assertThat(inventory.reservation(reference).state()).isEqualTo("RESERVED");
        assertThat(inventory.reservation(reference).quantities()).containsExactlyInAnyOrderEntriesOf(Map.of(a,3,b,2));
        assertThat(post(admin,path + "/stock",Map.of("delta",-7,"reason","盘点扣除全部可售量")).statusCode()).isEqualTo(200);
        assertThat(post(admin,path + "/stock",Map.of("delta",-1,"reason","不能扣预占")).statusCode()).isEqualTo(409);
        assertThat(post(admin,path + "/stock",Map.of("delta",2147483647,"reason","不能占用释放空间")).statusCode()).isEqualTo(409);
        assertThat(post(admin,path + "/price",Map.of("revision",3,"price","500.10","reason","预占后改价")).statusCode()).isEqualTo(200);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try {
            var first = executor.submit(() -> { start.await(); inventory.release(reference); return true; });
            var second = executor.submit(() -> { start.await(); inventory.release(reference); return true; });
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }
        assertThat(data(get(admin,path)).path("product").path("stock").asInt()).isEqualTo(3);
        assertThat(data(get(admin,path)).path("reservedStock").asInt()).isZero();
        assertThat(data(get(admin,"/api/admin/products/" + b)).path("product").path("stock").asInt()).isEqualTo(5);
        assertThat(inventory.reservation(reference).state()).isEqualTo("RELEASED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inventory.reserve(reference,Map.of(a,3,b,2))).isInstanceOf(CatalogException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inventory.reserve(prefix + "insufficient",Map.of(a,2,b,6))).isInstanceOf(CatalogException.class);
        assertThat(data(get(admin,path)).path("product").path("stock").asInt()).isEqualTo(3);
        assertThat(inventory.reservation(prefix + "insufficient")).isNull();
    }
    @Test void invalidInputsAndUnauthorizedWritersLeaveCurrentValuesUnchanged() throws Exception {
        long id = product("invalid");
        String path = "/api/admin/products/" + id;
        for (Object price : List.of("0","-1","1.234","10000000000.00","1e2","NaN",12.34)) {
            assertThat(post(admin,path + "/price",Map.of("revision",0,"price",price,"reason","非法金额")).statusCode()).as("%s",price).isEqualTo(400);
        }
        for (Object delta : List.of(0,-0.5,"2",2147483648L)) {
            assertThat(post(admin,path + "/stock",Map.of("delta",delta,"reason","非法数量")).statusCode()).isEqualTo(400);
        }
        assertThat(post(admin,path + "/stock",Map.of("delta",-1,"reason","负库存")).statusCode()).isEqualTo(409);
        for (String reason : List.of("", "  ", "长".repeat(501))) {
            assertThat(post(admin,path + "/price",Map.of("revision",0,"price","1","reason",reason)).statusCode()).isEqualTo(400);
            assertThat(post(admin,path + "/stock",Map.of("delta",1,"reason",reason)).statusCode()).isEqualTo(400);
        }
        assertThat(post(admin,path + "/stock",Map.of("delta",1,"reason","不能覆盖","reservedStock",0)).statusCode()).isEqualTo(400);
        assertThat(post(browser(),path + "/price",Map.of("revision",0,"price","1","reason","匿名")).statusCode()).isEqualTo(401);
        var user = browser();
        String username = prefix + "u";
        try {
            post(user,"/api/auth/register",Map.of("username",username,"password","PricePassword123!","confirmPassword","PricePassword123!"));
            post(user,"/api/auth/login",Map.of("username",username,"password","PricePassword123!"));
            assertThat(post(user,path + "/price",Map.of("revision",0,"price","1","reason","越权")).statusCode()).isEqualTo(403);
            assertThat(post(user,path + "/stock",Map.of("delta",1,"reason","越权")).statusCode()).isEqualTo(403);
            var noCsrf = admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path + "/stock"))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"delta\":1,\"reason\":\"无CSRF\"}")).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(noCsrf.statusCode()).isEqualTo(403);
        } finally { db.update("DELETE FROM account WHERE username=?",username); }
        assertThat(data(get(admin,path)).path("revision").asInt()).isZero();
        assertThat(data(get(admin,path)).path("product").path("price").asText()).isEqualTo("100.50");
        assertThat(data(get(admin,path)).path("product").path("stock").asInt()).isZero();
        assertThat(data(get(admin,path + "/audit")).size()).isEqualTo(1);
    }
    @Test void auditFailureRollsBackPriceStockAndRevisionAndPublicRevisionTracksChanges() throws Exception {
        long id = product("rollback");
        String path = "/api/admin/products/" + id;
        String constraint = "fault_" + prefix;
        db.execute("ALTER TABLE admin_audit ADD CONSTRAINT " + constraint + " CHECK (actor_username <> '" + prefix
                + "' OR object_type <> 'PRODUCT' OR object_id <> " + id + " OR action NOT IN ('PRICE','STOCK'))");
        try {
            assertThat(post(admin,path + "/price",Map.of("revision",0,"price","999.99","reason","审计故障")).statusCode()).isEqualTo(503);
            assertThat(post(admin,path + "/stock",Map.of("delta",9,"reason","审计故障")).statusCode()).isEqualTo(503);
            var current = data(get(admin,path));
            assertThat(current.path("product").path("price").asText()).isEqualTo("100.50");
            assertThat(current.path("product").path("stock").asInt()).isZero();
            assertThat(current.path("revision").asLong()).isZero();
            assertThat(data(get(admin,path + "/audit")).size()).isEqualTo(1);
        } finally { db.execute("ALTER TABLE admin_audit DROP CHECK " + constraint); }
        post(admin,path + "/price",Map.of("revision",0,"price","999.99","reason","恢复后调价"));
        assertThat(data(get(browser(),"/api/products/" + id)).path("product").path("revision").asLong()).isEqualTo(1);
        assertThat(data(get(browser(),"/api/products?keyword=" + prefix + "rollback")).get(0).path("revision").asLong()).isEqualTo(1);
    }
    @Test void overlappingReservationsUseOneLockOrderAndJoinTheCallersTransaction() throws Exception {
        long a = product("overlapA"), b = product("overlapB");
        for (long id : List.of(a,b)) post(admin,"/api/admin/products/" + id + "/stock",Map.of("delta",5,"reason","入库"));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try {
            var ascending = new LinkedHashMap<Long,Integer>(); ascending.put(a,2); ascending.put(b,2);
            var first = executor.submit(() -> { start.await(); return inventory.reserve(prefix + "first",ascending); });
            var reversed = new LinkedHashMap<Long,Integer>(); reversed.put(b,2); reversed.put(a,2);
            var second = executor.submit(() -> { start.await(); return inventory.reserve(prefix + "second",reversed); });
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS).state()).isEqualTo("RESERVED");
            assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS).state()).isEqualTo("RESERVED");
        } finally { executor.shutdownNow(); }
        assertThat(data(get(admin,"/api/admin/products/" + a)).path("product").path("stock").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inventory.reserve(prefix + "first",Map.of(a,1,b,2))).isInstanceOf(CatalogException.class);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            inventory.reserve(prefix + "outer",Map.of(a,1,b,1));
            status.setRollbackOnly();
        });
        assertThat(inventory.reservation(prefix + "outer")).isNull();
        assertThat(data(get(admin,"/api/admin/products/" + a)).path("product").path("stock").asInt()).isEqualTo(1);
        inventory.release(prefix + "first");
        inventory.release(prefix + "second");
        assertThat(data(get(admin,"/api/admin/products/" + a)).path("product").path("stock").asInt()).isEqualTo(5);
    }
    @Test void releaseReadsCommittedReservationItemsEvenWhenOuterTransactionHasAnOlderSnapshot() throws Exception {
        long id = product("oldSnapshot");
        post(admin,"/api/admin/products/" + id + "/stock",Map.of("delta",5,"reason","入库"));
        String reference = prefix + "lateReservation";
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(inventory.reservation(reference)).isNull(); // Establish the caller's old repeatable-read snapshot.
                try { executor.submit(() -> inventory.reserve(reference,Map.of(id,3))).get(15,java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception e) { throw new RuntimeException(e); }
                inventory.release(reference);
            });
        } finally { executor.shutdownNow(); }
        assertThat(inventory.reservation(reference).state()).isEqualTo("RELEASED");
        assertThat(data(get(admin,"/api/admin/products/" + id)).path("product").path("stock").asInt()).isEqualTo(5);
        assertThat(data(get(admin,"/api/admin/products/" + id)).path("reservedStock").asInt()).isZero();
    }
    long product(String suffix) throws Exception {
        var response = post(admin,"/api/admin/products",Map.of("sku",prefix + suffix,"category","PSU","price","100.50"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id = data(response).path("product").path("id").asLong();
        // Test fixture only: expose the SKU without coupling this ticket to all publishing parameters.
        db.update("UPDATE products SET status='ON_SALE' WHERE id=?",id);
        return id;
    }
    HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }
    HttpResponse<String> get(HttpClient client,String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(HttpClient client,String path,Object input) throws Exception {
        var csrf = data(get(client,"/api/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type","application/json")
                .header("X-CSRF-TOKEN",csrf.path("token").asText()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
}
