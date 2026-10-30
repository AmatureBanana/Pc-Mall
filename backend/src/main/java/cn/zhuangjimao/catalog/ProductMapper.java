package cn.zhuangjimao.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ProductMapper {
    @Select("""
            SELECT p.id, p.sku, p.name, p.category, c.label AS category_label,
                   p.brand, p.model, p.summary, p.image_url, p.price, p.stock,
                   p.source_url, p.price_note
            FROM products p JOIN product_categories c ON c.code = p.category
            WHERE p.status = 'ON_SALE'
            ORDER BY p.id
            """)
    List<Product> findOnSale();
}
