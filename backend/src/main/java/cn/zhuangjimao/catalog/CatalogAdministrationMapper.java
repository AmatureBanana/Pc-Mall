package cn.zhuangjimao.catalog;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
interface CatalogAdministrationMapper {
    String FILTER = """
        WHERE (#{category} IS NULL OR p.category=#{category}) AND (#{status} IS NULL OR p.status=#{status})
        AND LOCATE(#{keyword},CONCAT_WS(' ',p.sku,p.name,p.brand,p.model)) > 0
        """;
    @Select(ProductMapper.SELECT_PRODUCT + FILTER + " ORDER BY p.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Product> search(String category, String status, String keyword, int size, long offset);
    @Select("SELECT COUNT(*) FROM products p " + FILTER)
    long count(String category, String status, String keyword);
    @Select("SELECT revision FROM products WHERE id=#{id}")
    Long revision(long id);
    @Select("SELECT revision FROM products WHERE id=#{id} FOR UPDATE")
    Long lock(long id);
    @Insert("""
        INSERT INTO products(sku,category,name,brand,model,summary,image_url,price,stock,status,source_url,price_note,data_version)
        VALUES(#{sku},#{category},#{name},#{brand},#{model},#{summary},#{imageUrl},#{price},0,'OFF_SALE',#{sourceUrl},#{priceNote},'admin-v1')
        """)
    void create(CatalogAdministration.Fields fields);
    @Select("SELECT id FROM products WHERE sku=#{sku}")
    long idBySku(String sku);
    @Insert("""
        INSERT INTO product_specifications(product_id,description,parameters,data_version,verified_on)
        VALUES(#{id},#{description},#{parameters},#{version},CURRENT_DATE)
        ON DUPLICATE KEY UPDATE description=VALUES(description),parameters=VALUES(parameters),data_version=VALUES(data_version),verified_on=CURRENT_DATE
        """)
    void specification(long id, String description, String parameters, String version);
    @Update("""
        UPDATE products SET name=#{fields.name},brand=#{fields.brand},model=#{fields.model},summary=#{fields.summary},
        image_url=#{fields.imageUrl},source_url=#{fields.sourceUrl},price_note=#{fields.priceNote},revision=revision+1 WHERE id=#{id}
        """)
    void edit(long id, CatalogAdministration.Fields fields);
    @Update("UPDATE products SET status=#{status},revision=revision+1 WHERE id=#{id}")
    void status(long id, String status);
    @Select("""
        SELECT id,actor_id,actor_username,object_type,object_id,action,occurred_at,before_value,after_value
        FROM admin_audit WHERE object_type='PRODUCT' AND object_id=#{id} ORDER BY id DESC LIMIT 100
        """)
    List<AuditEntry> audit(long id);
    record AuditEntry(long id, long actorId, String actorUsername, String objectType, long objectId, String action,
                      java.time.LocalDateTime occurredAt, String beforeValue, String afterValue) {}
}
