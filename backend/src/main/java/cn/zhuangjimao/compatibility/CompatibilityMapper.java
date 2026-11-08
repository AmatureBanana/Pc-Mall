package cn.zhuangjimao.compatibility;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Reads the authoritative catalog rows the rules depend on; never accepts client parameters. */
@Mapper
interface CompatibilityMapper {
    @Select("""
        SELECT p.id, p.category, p.status, s.parameters
        FROM products p LEFT JOIN product_specifications s ON s.product_id = p.id
        WHERE p.id = #{id}
        """)
    Record find(long id);

    record Record(long id, String category, String status, String parameters) {}
}
