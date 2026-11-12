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
        INSERT INTO order_item(order_number,product_id,sku,name,model,category,price,quantity,amount)
        VALUES(#{number},#{item.productId},#{item.sku},#{item.name},#{item.model},#{item.category},#{item.price},#{item.quantity},#{item.amount})
        """)
    void snapshot(String number, ShoppingCart.Item item);

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline FROM mall_order WHERE account_id=#{accountId} AND order_number=#{number}")
    Header order(long accountId, String number);

    @Select("SELECT product_id,sku,name,model,category,price,quantity,amount,configuration_id FROM order_item WHERE order_number=#{number} ORDER BY id")
    List<SingleItemOrders.Item> items(String number);

    record RequestRow(String fingerprint, String orderNumber) {}
    record Header(String orderNumber, String status, String currency, BigDecimal total, LocalDateTime createdAt, LocalDateTime paymentDeadline) {}
}
