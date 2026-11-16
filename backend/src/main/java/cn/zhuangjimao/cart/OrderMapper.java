package cn.zhuangjimao.cart;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
interface OrderMapper {
    @Select("SELECT fingerprint,order_number FROM order_request WHERE account_id=#{accountId} AND operation='CREATE_ORDER' AND request_key=#{key} FOR UPDATE")
    RequestRow request(long accountId, String key);

    @Insert("INSERT INTO order_request(account_id,operation,request_key,fingerprint,order_number) VALUES(#{accountId},'CREATE_ORDER',#{key},#{fingerprint},#{number})")
    void remember(long accountId, String key, String fingerprint, String number);

    @Insert("""
        INSERT INTO mall_order(order_number,account_id,reservation_reference,status,currency,total,created_at,payment_deadline)
        VALUES(#{number},#{accountId},#{number},'PENDING_PAYMENT','CNY',#{total},#{createdAt},#{deadline})
        """)
    void create(String number, long accountId, BigDecimal total, LocalDateTime createdAt, LocalDateTime deadline);

    @Insert("""
        INSERT INTO order_item(order_number,product_id,sku,name,model,category,price,quantity,amount,configuration_id)
        VALUES(#{number},#{item.productId},#{item.sku},#{item.name},#{item.model},#{item.category},#{item.price},#{item.quantity},#{item.amount},#{configurationId})
        """)
    void snapshot(String number, ShoppingCart.Item item, Long configurationId);

    @Insert("INSERT INTO order_configuration(order_number,configuration_id,amount) VALUES(#{number},#{group.id},#{group.amount})")
    void configuration(String number, ShoppingCart.Configuration group);

    @Select("SELECT configuration_id AS id,amount FROM order_configuration WHERE order_number=#{number} ORDER BY configuration_id")
    List<SingleItemOrders.Configuration> configurations(String number);

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline,paid_at FROM mall_order WHERE account_id=#{accountId} AND order_number=#{number}")
    Header order(long accountId, String number);

    @Select("SELECT COUNT(*) FROM mall_order WHERE account_id=#{accountId}")
    long count(long accountId);

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline,paid_at FROM mall_order WHERE account_id=#{accountId} ORDER BY created_at DESC,order_number DESC LIMIT 20 OFFSET #{offset}")
    List<Header> history(long accountId, long offset);

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline,paid_at FROM mall_order WHERE account_id=#{accountId} AND order_number=#{number} FOR UPDATE")
    Header lockOrder(long accountId, String number);

    @Update("UPDATE mall_order SET status='PAID',paid_at=#{now} WHERE account_id=#{accountId} AND order_number=#{number} AND status='PENDING_PAYMENT' AND payment_deadline>#{now}")
    int pay(long accountId, String number, LocalDateTime now);

    @Select("SELECT product_id,sku,name,model,category,price,quantity,amount,configuration_id FROM order_item WHERE order_number=#{number} ORDER BY id")
    List<SingleItemOrders.Item> items(String number);

    record RequestRow(String fingerprint, String orderNumber) {}
    record Header(String orderNumber, String status, String currency, BigDecimal total, LocalDateTime createdAt, LocalDateTime paymentDeadline, LocalDateTime paidAt) {}
}
