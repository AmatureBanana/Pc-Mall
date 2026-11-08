package cn.zhuangjimao.cart;

import cn.zhuangjimao.auth.AccountPrincipal;
import cn.zhuangjimao.catalog.DiyMemory;
import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Set;

/**
 * Owner-scoped cart endpoints. The account always comes from the session principal; no request
 * field can name a different owner, and {@code WebSecurity} keeps visitors out with 401 before
 * any method here runs.
 */
@RestController
@RequestMapping("/api/cart")
class CartController {
    private final ShoppingCart carts;

    CartController(ShoppingCart carts) { this.carts = carts; }

    @GetMapping
    ResponseEntity<CartResponse> view(@AuthenticationPrincipal AccountPrincipal principal) {
        return answer(carts.view(principal.ownerId()));
    }

    @PostMapping("/items")
    ResponseEntity<CartResponse> add(@AuthenticationPrincipal AccountPrincipal principal, @RequestBody JsonNode input) {
        requireFields(input, Set.of("productId", "quantity", "requestKey"));
        return answer(carts.add(principal.ownerId(), positive(input, "productId"),
                quantity(input, "quantity"), text(input, "requestKey")));
    }

    @PostMapping("/items/{itemId}/quantity")
    ResponseEntity<CartResponse> quantity(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long itemId,
                                          @RequestBody JsonNode input) {
        requireFields(input, Set.of("quantity"));
        return answer(carts.setQuantity(principal.ownerId(), itemId, quantity(input, "quantity")));
    }

    /** A body-less DELETE still carries the session's CSRF token, checked by the shared chain. */
    @DeleteMapping("/items/{itemId}")
    ResponseEntity<CartResponse> remove(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long itemId) {
        return answer(carts.remove(principal.ownerId(), itemId));
    }

    @PostMapping("/configurations")
    ResponseEntity<CartResponse> addConfiguration(@AuthenticationPrincipal AccountPrincipal principal, @RequestBody JsonNode input) {
        requireFields(input, Set.of("selections", "integratedGraphicsCpuId", "memoryQuantity"));
        var entries = input.get("selections");
        if (entries == null || !entries.isArray() || entries.size() < 7 || entries.size() > 8) {
            invalid("请选择完整的装机配置。");
        }
        var selections = new EnumMap<HardwareCategory, Long>(HardwareCategory.class);
        for (var entry : entries) {
            requireFields(entry, Set.of("category", "productId"));
            HardwareCategory category;
            try { category = HardwareCategory.valueOf(text(entry, "category")); }
            catch (IllegalArgumentException error) {
                invalid("配置商品类别无效。");
                throw error;
            }
            if (selections.putIfAbsent(category, positive(entry, "productId")) != null) {
                invalid("每个硬件类别只能选择一件商品。");
            }
        }
        Long graphics = input.has("integratedGraphicsCpuId") ? positive(input, "integratedGraphicsCpuId") : null;
        long memoryQuantity = input.has("memoryQuantity") ? positive(input, "memoryQuantity") : 1;
        if (!DiyMemory.validQuantity(memoryQuantity, selections.containsKey(HardwareCategory.MEMORY))) {
            invalid("所选内存数量须为 1～4 根。");
        }
        return answer(carts.addConfiguration(principal.ownerId(), selections, graphics, (int) memoryQuantity));
    }

    @DeleteMapping("/configurations/{id}")
    ResponseEntity<CartResponse> removeConfiguration(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return answer(carts.removeConfiguration(principal.ownerId(), id));
    }

    private static ResponseEntity<CartResponse> answer(ShoppingCart.CartView view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CartResponse(view));
    }

    /** Rejects both unknown fields and client-claimed ownership before the service sees anything. */
    private static void requireFields(JsonNode input, Set<String> allowed) {
        if (input == null || !input.isObject()) invalid("请提交购物车对象。");
        input.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) invalid("不允许提交字段：" + key); });
    }

    private static long positive(JsonNode input, String key) {
        var value = input.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            invalid("商品 ID 必须是正整数。");
        }
        return value.asLong();
    }

    private static int quantity(JsonNode input, String key) {
        var value = input.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) invalid("数量必须是整数。");
        return value.asInt();
    }

    private static String text(JsonNode input, String key) {
        var value = input.get(key);
        if (value == null || !value.isTextual()) invalid("请求标识必须是文本。");
        return value.asText();
    }

    private static void invalid(String message) {
        throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_CART_INPUT", message);
    }

    record CartResponse(ShoppingCart.CartView data) {}
}
