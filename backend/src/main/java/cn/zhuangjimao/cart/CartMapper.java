package cn.zhuangjimao.cart;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cart persistence only. Every statement touches the cart tables; the product table is read for
 * current price, sale state and available stock and is never locked, reserved or decremented here.
 */
@Mapper
interface CartMapper {
    /** Creates the one logical cart on first use; repeated calls keep the same row. */
    @Insert("INSERT INTO shopping_cart(account_id) VALUES(#{accountId}) ON DUPLICATE KEY UPDATE account_id=VALUES(account_id)")
    void ensureCart(long accountId);

    @Select("SELECT id,revision FROM shopping_cart WHERE account_id=#{accountId}")
    CartRow cart(long accountId);

    /** Serializes every write of one owner's cart, so concurrent adds cannot lose a quantity. */
    @Select("SELECT id,revision FROM shopping_cart WHERE account_id=#{accountId} FOR UPDATE")
    CartRow lockCart(long accountId);

    @Update("UPDATE shopping_cart SET revision=revision+1 WHERE id=#{cartId}")
    int bumpRevision(long cartId);

    @Select("""
        SELECT i.id,i.product_id,i.quantity,
               p.sku,p.name,p.category,c.label AS category_label,p.brand,p.model,p.image_url,
               p.price,p.stock,p.status
        FROM shopping_cart_item i
        JOIN products p ON p.id=i.product_id
        JOIN product_categories c ON c.code=p.category
        WHERE i.cart_id=#{cartId}
        ORDER BY i.id
        """)
    List<LineView> lines(long cartId);

    @Select("SELECT id,cart_id,product_id,quantity FROM shopping_cart_item WHERE cart_id=#{cartId} AND product_id=#{productId}")
    LineRow lineForProduct(long cartId, long productId);

    @Select("SELECT id,cart_id,product_id,quantity FROM shopping_cart_item WHERE id=#{itemId}")
    LineRow line(long itemId);

    /** Current catalog data the write path validates against. No product row is locked or changed. */
    @Select("SELECT id,sku,status,stock,price,category FROM products WHERE id=#{productId}")
    StockRow product(long productId);

    @Insert("INSERT INTO shopping_cart_item(cart_id,product_id,quantity) VALUES(#{cartId},#{productId},#{quantity})")
    void insertItem(long cartId, long productId, int quantity);

    @Update("UPDATE shopping_cart_item SET quantity=#{quantity} WHERE id=#{itemId}")
    int quantity(long itemId, int quantity);

    @Update("DELETE FROM shopping_cart_item WHERE id=#{itemId}")
    int deleteItem(long itemId);

    @Select("SELECT id FROM cart_configuration WHERE cart_id=#{cartId} AND signature=#{signature}")
    Long configurationId(long cartId, String signature);

    @Insert("INSERT INTO cart_configuration(cart_id,signature) VALUES(#{cartId},#{signature})")
    void insertConfiguration(long cartId, String signature);

    @Insert("INSERT INTO cart_configuration_item(configuration_id,product_id,quantity) VALUES(#{configurationId},#{productId},#{quantity})")
    void insertConfigurationItem(long configurationId, long productId, int quantity);

    @Update("DELETE FROM cart_configuration WHERE cart_id=#{cartId} AND id=#{configurationId}")
    int deleteConfiguration(long cartId, long configurationId);

    @Select("""
        SELECT g.id,p.id AS product_id,i.quantity,
               p.sku,p.name,p.category,c.label AS category_label,p.brand,p.model,p.image_url,
               p.price,p.stock,p.status
        FROM cart_configuration g
        JOIN cart_configuration_item i ON i.configuration_id=g.id
        JOIN products p ON p.id=i.product_id
        JOIN product_categories c ON c.code=p.category
        WHERE g.cart_id=#{cartId}
        ORDER BY g.id,p.category
        """)
    List<LineView> configurationLines(long cartId);

    @Select("""
        SELECT COALESCE(SUM(i.quantity),0) FROM cart_configuration_item i
        JOIN cart_configuration g ON g.id=i.configuration_id
        WHERE g.cart_id=#{cartId} AND i.product_id=#{productId}
        """)
    long configurationQuantity(long cartId, long productId);

    @Insert("""
        INSERT INTO cart_request(account_id,request_key,fingerprint,creation_token)
        VALUES(#{accountId},#{requestKey},#{fingerprint},#{token})
        ON DUPLICATE KEY UPDATE account_id=VALUES(account_id)
        """)
    void ensureRequest(long accountId, String requestKey, String fingerprint, String token);

    @Select("""
        SELECT fingerprint,creation_token,result FROM cart_request
        WHERE account_id=#{accountId} AND request_key=#{requestKey} FOR UPDATE
        """)
    RequestRow lockRequest(long accountId, String requestKey);

    @Update("UPDATE cart_request SET result=#{result} WHERE account_id=#{accountId} AND request_key=#{requestKey}")
    int completeRequest(long accountId, String requestKey, String result);

    record CartRow(long id, long revision) {}
    record LineRow(long id, long cartId, long productId, int quantity) {}
    record StockRow(long id, String sku, String status, int stock, BigDecimal price, String category) {}
    record RequestRow(String fingerprint, String creationToken, String result) {}
    record LineView(long id, long productId, int quantity, String sku, String name, String category,
                    String categoryLabel, String brand, String model, String imageUrl,
                    BigDecimal price, int stock, String status) {}
}
