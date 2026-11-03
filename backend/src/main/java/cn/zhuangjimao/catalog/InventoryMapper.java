package cn.zhuangjimao.catalog;

import org.apache.ibatis.annotations.*;

@Mapper
interface InventoryMapper {
    @Insert("""
        INSERT INTO stock_reservations(reference,state,quantities,creation_token) VALUES(#{reference},'RESERVED',#{quantities},#{token})
        ON DUPLICATE KEY UPDATE reference=VALUES(reference)
        """)
    void ensureReservation(String reference, String quantities, String token);
    @Select("SELECT state,quantities,creation_token FROM stock_reservations WHERE reference=#{reference} FOR UPDATE")
    ReservationRow lockReservation(String reference);
    @Select("SELECT state,quantities,creation_token FROM stock_reservations WHERE reference=#{reference}")
    ReservationRow reservation(String reference);
    @Insert("INSERT INTO stock_reservation_items(reference,product_id,quantity) VALUES(#{reference},#{id},#{quantity})")
    void item(String reference, long id, int quantity);
    @Select("SELECT id,stock,reserved_stock,revision,status FROM products WHERE id=#{id} FOR UPDATE")
    Balance lock(long id);
    @Update("""
        UPDATE products SET stock=stock+#{available},reserved_stock=reserved_stock+#{reserved},revision=revision+1
        WHERE id=#{id} AND revision=#{revision} AND stock+#{available} >= 0 AND reserved_stock+#{reserved} >= 0
        AND stock+reserved_stock+#{available}+#{reserved} <= 2147483647
        """)
    int change(long id, long revision, int available, int reserved);
    @Update("UPDATE stock_reservations SET state='RELEASED' WHERE reference=#{reference} AND state='RESERVED'")
    int release(String reference);
    record ReservationRow(String state, String quantities, String creationToken) {}
    record Balance(long id, int stock, int reservedStock, long revision, String status) {}
}
