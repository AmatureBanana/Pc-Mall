package cn.zhuangjimao.cart;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
interface OrderMapper {
    @Select("SELECT a.id,a.username,a.nickname FROM account a JOIN mall_order o ON o.account_id=a.id WHERE o.order_number=#{number}")
    OrderAdministration.Buyer buyer(String number);

    String ADMIN_FILTER = """
        FROM mall_order o JOIN account a ON a.id=o.account_id WHERE 1=1
        <if test="query.orderNumber != ''">AND CONVERT(o.order_number USING utf8mb4) LIKE #{query.orderNumber} ESCAPE '!'</if>
        <if test="query.username != ''">AND a.username LIKE #{query.username} ESCAPE '!'</if>
        <if test="query.status != null and query.status != ''">AND o.status=#{query.status}</if>
        <if test="query.accountId != null">AND o.account_id=#{query.accountId}</if>
        """;

    @Select("<script>SELECT COUNT(*) " + ADMIN_FILTER + "</script>")
    long adminCount(@Param("query") OrderAdministration.Query query);

    @Select("<script>SELECT o.order_number " + ADMIN_FILTER + " ORDER BY o.created_at DESC,o.order_number DESC LIMIT #{query.pageSize} OFFSET #{offset}</script>")
    List<String> adminNumbers(OrderAdministration.Query query, long offset);

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

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline,paid_at,reservation_reference,cancelled_at,cancellation_reason,completed_at,completed_by FROM mall_order WHERE account_id=#{accountId} AND order_number=#{number}")
    Header order(long accountId, String number);

    String HISTORY_FILTER = """
        FROM mall_order o WHERE o.account_id=#{accountId}
        <if test="query.keyword != null and query.keyword != ''">
          AND (CONVERT(o.order_number USING utf8mb4) LIKE #{query.keyword} ESCAPE '!'
            OR EXISTS (SELECT 1 FROM order_item i WHERE i.order_number=o.order_number AND i.name LIKE #{query.keyword} ESCAPE '!'))
        </if>
        <if test="query.startDate != null">AND o.created_at &gt;= #{query.from}</if>
        <if test="query.endDate != null">AND o.created_at &lt; #{query.until}</if>
        """;

    @Select("<script>SELECT o.status,COUNT(*) AS count " + HISTORY_FILTER + " GROUP BY o.status</script>")
    List<StateCount> counts(long accountId, SingleItemOrders.HistoryQuery query);

    @Select("""
        <script>SELECT o.order_number,o.status,o.currency,o.total,o.created_at,o.payment_deadline,o.paid_at,
          o.reservation_reference,o.cancelled_at,o.cancellation_reason,o.completed_at,o.completed_by
        """ + HISTORY_FILTER + """
        <if test="query.status != null and query.status != ''">AND o.status=#{query.status}</if>
        ORDER BY o.created_at DESC,o.order_number DESC LIMIT #{query.pageSize} OFFSET #{offset}</script>
        """)
    List<Header> history(long accountId, SingleItemOrders.HistoryQuery query, long offset);

    @Select("SELECT order_number,status,currency,total,created_at,payment_deadline,paid_at,reservation_reference,cancelled_at,cancellation_reason,completed_at,completed_by FROM mall_order WHERE account_id=#{accountId} AND order_number=#{number} FOR UPDATE")
    Header lockOrder(long accountId, String number);

    @Update("UPDATE mall_order SET status='PAID',paid_at=#{now} WHERE account_id=#{accountId} AND order_number=#{number} AND status='PENDING_PAYMENT' AND payment_deadline>#{now}")
    int pay(long accountId, String number, LocalDateTime now);

    @Select("SELECT id FROM mall_order WHERE order_number=#{number}")
    long auditId(String number);

    @Update("UPDATE mall_order SET status='COMPLETED',completed_at=#{now},completed_by=#{actor} WHERE order_number=#{number} AND status='PAID'")
    int complete(String number, LocalDateTime now, String actor);

    @Update("UPDATE mall_order SET status='CANCELLED',cancelled_at=#{now},cancellation_reason=#{reason} WHERE order_number=#{number} AND status='PENDING_PAYMENT'")
    int cancel(String number, LocalDateTime now, String reason);

    @Select("""
        SELECT order_number,account_id FROM mall_order
        WHERE status='PENDING_PAYMENT' AND payment_deadline<=#{cutoff} AND order_number>#{after}
        ORDER BY order_number LIMIT 100
        """)
    List<Due> expired(LocalDateTime cutoff, String after);

    @Select("SELECT product_id,sku,name,model,category,price,quantity,amount,configuration_id FROM order_item WHERE order_number=#{number} ORDER BY id")
    List<SingleItemOrders.Item> items(String number);

    record RequestRow(String fingerprint, String orderNumber) {}
    record Due(String orderNumber, long accountId) {}
    record StateCount(String status, long count) {}
    record Header(String orderNumber, String status, String currency, BigDecimal total, LocalDateTime createdAt,
                  LocalDateTime paymentDeadline, LocalDateTime paidAt, String reservationReference,
                  LocalDateTime cancelledAt, String cancellationReason, LocalDateTime completedAt, String completedBy) {}
}
