package cn.zhuangjimao.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ProductMapper {
    String SELECT_PRODUCT = """
            SELECT p.id, p.sku, p.name, p.category, c.label AS category_label,
                   p.brand, p.model, p.summary, p.image_url, p.price, p.stock,
                   p.source_url, p.price_note, p.status
            FROM products p JOIN product_categories c ON c.code = p.category
            """;

    @Select(SELECT_PRODUCT + """
            WHERE p.status = 'ON_SALE'
              AND (#{category} IS NULL OR p.category = #{category})
              AND LOCATE(#{keyword}, CONCAT_WS(' ', p.name, p.brand, p.model, p.sku, p.summary)) > 0
            ORDER BY p.id
            """)
    List<Product> findOnSale(String category, String keyword);

    @Select(SELECT_PRODUCT + " WHERE p.id = #{id}")
    Product findById(long id);

    @Select("SELECT description, parameters, data_version FROM product_specifications WHERE product_id = #{id}")
    Specification findSpecification(long id);

    record Specification(String description, String parameters, String dataVersion) {}
}
