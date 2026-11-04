package cn.zhuangjimao.catalog;

import org.apache.ibatis.annotations.*;

@Mapper
interface PerformanceMapper {
    @Select("SELECT reference_data, identity_data FROM product_performance WHERE product_id=#{id}")
    Stored find(long id);
    @Insert("""
        INSERT INTO product_performance(product_id,reference_data,identity_data) VALUES(#{id},#{reference},#{identity})
        ON DUPLICATE KEY UPDATE reference_data=VALUES(reference_data),identity_data=VALUES(identity_data)
        """)
    void save(long id,String reference,String identity);
    @Update("UPDATE products SET revision=revision+1 WHERE id=#{id} AND revision=#{revision}")
    int advance(long id,long revision);
    record Stored(String referenceData,String identityData) {}
}
