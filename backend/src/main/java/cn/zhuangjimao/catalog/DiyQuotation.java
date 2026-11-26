package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityService;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A read-only current-catalog snapshot; does not reserve stock or persist guest workspaces. */
@Service
class DiyQuotation {
    private final ProductMapper products;
    private final DiyPerformance performance;
    private final CompatibilityService compatibility;

    DiyQuotation(ProductMapper products, DiyPerformance performance, CompatibilityService compatibility) {
        this.products = products;
        this.performance = performance;
        this.compatibility = compatibility;
    }

    @Transactional(readOnly = true)
    public Quote quote(long revision, Map<HardwareCategory, Long> selections, int memoryQuantity) throws Exception {
        return quote(revision, selections, memoryQuantity, false, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Quote quote(long revision, Map<HardwareCategory, Long> selections, int memoryQuantity,
                       boolean assessPerformance, boolean integrated) throws Exception {
        var items = new ArrayList<Item>();
        BigDecimal subtotal = new BigDecimal("0.00");
        for (var category : HardwareCategory.values()) {
            Long id = selections.get(category);
            if (id == null) continue;
            int quantity = category == HardwareCategory.MEMORY ? memoryQuantity : 1;
            Product product = products.findById(id);
            String state = "VALID";
            String message = category == HardwareCategory.MEMORY ? quantity + " 根内存" : "一个销售单位";
            if (product == null) { state = "NOT_FOUND"; message = "商品不存在，请移除或替换。"; }
            else if (!category.name().equals(product.category())) { state = "CATEGORY_MISMATCH"; message = "商品类别不匹配，请替换。"; }
            else if (!"ON_SALE".equals(product.status())) { state = "OFF_SALE"; message = "商品已下架，请替换。"; }
            else if (product.stock() < 1) { state = "OUT_OF_STOCK"; message = "商品暂时缺货，请替换。"; }
            else if (product.stock() < quantity) { state = "OUT_OF_STOCK"; message = "内存库存不足，当前仅可选 " + product.stock() + " 根。"; }
            BigDecimal amount = state.equals("VALID") ? product.price().multiply(BigDecimal.valueOf(quantity)) : null;
            if (amount != null) subtotal = subtotal.add(amount);
            items.add(new Item(category, id, quantity, state, message, product, amount));
        }
        boolean valid = items.stream().allMatch(item -> item.state().equals("VALID"));
        var assessment = assessPerformance ? performance.assess(items, integrated) : null;
        var integratedCpuId = integrated ? selections.get(HardwareCategory.CPU) : null;
        if (assessment != null && assessment.score() != null
                && !compatibility.check(revision, selections, integratedCpuId, memoryQuantity).corePassed())
            assessment = null;
        var displayItems = items.stream().map(item -> item.product() == null ? item : new Item(
                item.category(), item.productId(), item.quantity(), item.state(), item.message(),
                CatalogBrandNames.localized(item.product()), item.amount())).toList();
        return new Quote(revision, displayItems, valid ? subtotal : null, subtotal, "NOT_CHECKED", assessment);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Item(HardwareCategory category, long productId, int quantity, String state, String message,
                Product product, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Quote(long revision, List<Item> items,
                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal validSubtotal,
                 String compatibility, @JsonInclude(JsonInclude.Include.NON_NULL) DiyPerformance.Assessment assessment) {}
}
