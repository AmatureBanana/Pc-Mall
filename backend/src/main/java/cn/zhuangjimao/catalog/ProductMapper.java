package cn.zhuangjimao.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
interface ProductMapper {
    String SELECT_PRODUCT = """
            SELECT p.id, p.sku, p.name, p.category, c.label AS category_label,
                   p.brand, p.model, p.summary, p.image_url, p.price, p.stock,
                   p.source_url, p.price_note, p.status, p.revision
            FROM products p JOIN product_categories c ON c.code = p.category
            """;

    @Select(SELECT_PRODUCT + """
            WHERE p.status = 'ON_SALE'
              AND (#{category} IS NULL OR p.category = #{category})
              AND (LOCATE(#{keyword}, CONCAT_WS(' ', p.name, p.brand, p.model, p.sku, p.summary)) > 0
                   OR LOCATE(#{manufacturerKeyword}, CONCAT_WS(' ', p.name, p.brand, p.model, p.sku, p.summary)) > 0
                   OR EXISTS (
                       SELECT 1
                       FROM product_specifications s
                       CROSS JOIN JSON_TABLE(s.parameters, '$[*]' COLUMNS (
                           parameter_label VARCHAR(128) PATH '$.label',
                           parameter_value JSON PATH '$.value',
                           parameter_unit VARCHAR(32) PATH '$.unit'
                       )) spec
                       WHERE s.product_id = p.id AND spec.parameter_value IS NOT NULL
                         AND (LOCATE(#{keyword}, CONCAT_WS(' ', spec.parameter_label, JSON_UNQUOTE(spec.parameter_value),
                                                           NULLIF(spec.parameter_unit, '')) COLLATE utf8mb4_0900_ai_ci) > 0
                              OR LOCATE(#{keyword}, CONCAT(JSON_UNQUOTE(spec.parameter_value),
                                                           COALESCE(spec.parameter_unit, '')) COLLATE utf8mb4_0900_ai_ci) > 0)
                   ))
            ORDER BY p.id
            """)
    List<Product> findOnSale(String category, String keyword, String manufacturerKeyword);

    @Select(SELECT_PRODUCT + " WHERE p.id = #{id}")
    Product findById(long id);

    @Select("SELECT description, parameters, data_version FROM product_specifications WHERE product_id = #{id}")
    Specification findSpecification(long id);

    record Specification(String description, String parameters, String dataVersion) {}

    @Select("""
        <script>
        SELECT p.id AS product_id,s.parameters,r.reference_data,r.identity_data
        FROM products p LEFT JOIN product_specifications s ON s.product_id=p.id
        LEFT JOIN product_performance r ON r.product_id=p.id
        WHERE p.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<TagSource> tagSources(@Param("ids") List<Long> ids);
    record TagSource(long productId,String parameters,String referenceData,String identityData) {}
}
