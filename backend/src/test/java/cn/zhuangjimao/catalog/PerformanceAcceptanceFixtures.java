package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

final class PerformanceAcceptanceFixtures {
    static final Path DATA = Path.of("data", "performance-acceptance-v1");
    static final String VERSION = "performance-acceptance-v1";
    static final int STOCK = 500;
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final String prefix = "p24" + UUID.randomUUID().toString().substring(0, 8);

    PerformanceAcceptanceFixtures(JdbcTemplate db, TransactionTemplate transaction, ObjectMapper json) {
        this.db = db;
        this.transaction = transaction;
        this.json = json;
    }

    void install() throws IOException {
        var products = json.readTree(DATA.resolve("catalog.json").toFile());
        var references = new HashMap<String, JsonNode>();
        for (var reference : json.readTree(DATA.resolve("rendering.json").toFile())) {
            references.put(reference.path("deviceName").asText(), reference);
        }
        transaction.executeWithoutResult(status -> {
            for (var product : products) {
                String category = product.path("category").asText();
                String sku = name(product.path("sku").asText());
                String brand = product.path("brand").asText();
                String model = product.path("model").asText();
                String summary = product.path("summary").asText();
                var parameters = product.path("parameters");
                db.update("""
                        INSERT INTO products(sku, name, category, brand, model, summary, image_url, price, stock,
                                             status, source_url, price_note, data_version)
                        VALUES(?,?,?,?,?,?,?,?,?,'ON_SALE',?,'CNY 演示价格与库存，非实时市场报价',?)
                        """, sku, product.path("name").asText(), category, brand, model, summary,
                        product.path("image").asText(), new BigDecimal(product.path("price").asText()),
                        STOCK, product.path("source").asText(), VERSION);
                long id = idBySku(sku);
                db.update("""
                        INSERT INTO product_specifications(product_id, description, parameters, data_version, verified_on)
                        VALUES(?,?,?,?, '2026-11-24')
                        """, id, summary, parameters.toString(), VERSION);
                if (product.has("reference")) {
                    var reference = references.get(product.path("reference").asText());
                    var identity = json.createObjectNode().put("category", category).put("brand", brand).put("model", model);
                    if (category.equals("GPU")) {
                        for (var field : parameters) {
                            String code = field.path("code").asText();
                            if (code.equals("boardVariant") || code.equals("vram")) identity.set(code, field.get("value"));
                        }
                    }
                    db.update("INSERT INTO product_performance(product_id, reference_data, identity_data) VALUES(?,?,?)",
                            id, reference.toString(), identity.toString());
                }
            }
        });
    }

    private String name(String suffix) { return prefix + "-" + suffix; }

    String username(String suffix) { return prefix + "u" + suffix; }

    private String glob() { return prefix + "-%"; }

    long idBySku(String sku) {
        return db.queryForObject("SELECT id FROM products WHERE sku=?", Long.class, sku);
    }

    List<Long> storageProducts() {
        return db.queryForList("SELECT id FROM products WHERE sku LIKE ? AND category='SSD' ORDER BY id", Long.class, glob());
    }

    boolean ownsProduct(long id) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM products WHERE id=? AND sku LIKE ?)",
                Boolean.class, id, glob()));
    }

    void resetFixtureStock(long id, int quantity) {
        if (db.update("UPDATE products SET stock=? WHERE id=? AND sku LIKE ?", quantity, id, glob()) != 1) {
            throw new IllegalStateException("Not a product installed by this run");
        }
    }

    void cleanUp() {
        String owners = username("%");
        transaction.executeWithoutResult(status -> {
            var reservations = db.queryForList("""
                    SELECT o.reservation_reference FROM mall_order o JOIN account a ON o.account_id=a.id WHERE a.username LIKE ?
                    """, String.class, owners);
            db.update("DELETE FROM admin_audit WHERE actor_username LIKE ?", owners);
            db.update("DELETE r FROM order_request r JOIN account a ON r.account_id=a.id WHERE a.username LIKE ?", owners);
            db.update("DELETE o FROM mall_order o JOIN account a ON o.account_id=a.id WHERE a.username LIKE ?", owners);
            for (var reference : reservations) {
                db.update("DELETE FROM stock_reservation_items WHERE reference=?", reference);
                db.update("DELETE FROM stock_reservations WHERE reference=?", reference);
            }
            db.update("DELETE r FROM cart_request r JOIN account a ON r.account_id=a.id WHERE a.username LIKE ?", owners);
            for (String table : List.of("shopping_cart_item", "cart_configuration")) {
                db.update("DELETE i FROM " + table + " i JOIN shopping_cart c ON i.cart_id=c.id "
                        + "JOIN account a ON c.account_id=a.id WHERE a.username LIKE ?", owners);
            }
            db.update("DELETE c FROM shopping_cart c JOIN account a ON c.account_id=a.id WHERE a.username LIKE ?", owners);
            db.update("DELETE FROM account WHERE username LIKE ?", owners);
            for (String table : List.of("product_performance", "product_specifications")) {
                db.update("DELETE s FROM " + table + " s JOIN products p ON s.product_id=p.id WHERE p.sku LIKE ?", glob());
            }
            db.update("DELETE FROM products WHERE sku LIKE ?", glob());
        });
    }
}
