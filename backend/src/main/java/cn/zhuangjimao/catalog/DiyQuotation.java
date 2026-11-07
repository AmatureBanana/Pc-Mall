package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A read-only current-catalog snapshot; does not reserve stock or persist guest workspaces. */
@Service
class DiyQuotation {
    private final ProductMapper products;

    DiyQuotation(ProductMapper products) { this.products = products; }

    @Transactional(readOnly = true)
    public Quote quote(long revision, Map<HardwareCategory, Long> selections) {
        var items = new ArrayList<Item>();
        BigDecimal subtotal = new BigDecimal("0.00");
        for (var category : HardwareCategory.values()) {
            Long id = selections.get(category);
            if (id == null) continue;
            Product product = products.findById(id);
            String state = "VALID";
            String message = "一个销售单位";
            if (product == null) { state = "NOT_FOUND"; message = "商品不存在，请移除或替换。"; }
            else if (!category.name().equals(product.category())) { state = "CATEGORY_MISMATCH"; message = "商品类别不匹配，请替换。"; }
            else if (!"ON_SALE".equals(product.status())) { state = "OFF_SALE"; message = "商品已下架，请替换。"; }
            else if (product.stock() < 1) { state = "OUT_OF_STOCK"; message = "商品暂时缺货，请替换。"; }
            BigDecimal amount = state.equals("VALID") ? product.price() : null;
            if (amount != null) subtotal = subtotal.add(amount);
            items.add(new Item(category, id, 1, state, message, product, amount));
        }
        boolean valid = items.stream().allMatch(item -> item.state().equals("VALID"));
        return new Quote(revision, List.copyOf(items), valid ? subtotal : null, subtotal, "NOT_CHECKED");
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Item(HardwareCategory category, long productId, int quantity, String state, String message,
                Product product, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Quote(long revision, List<Item> items,
                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal validSubtotal,
                 String compatibility) {}
}
