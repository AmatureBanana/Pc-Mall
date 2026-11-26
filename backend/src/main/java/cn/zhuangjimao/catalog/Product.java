package cn.zhuangjimao.catalog;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;

record Product(long id, String sku, String name, String category, String categoryLabel,
               String brand, String model, String summary, String imageUrl,
               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price, int stock,
               String sourceUrl, String priceNote, String status, long revision) {
    Product withSummary(String displaySummary) {
        return new Product(id, sku, name, category, categoryLabel, brand, model, displaySummary, imageUrl,
                price, stock, sourceUrl, priceNote, status, revision);
    }
}
