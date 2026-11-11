package cn.zhuangjimao.cart;

import cn.zhuangjimao.catalog.HardwareCategory;
import cn.zhuangjimao.compatibility.CompatibilityService;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The persistent cart. Ownership always comes from the authenticated session, so a
 * caller cannot name another account. Reading and writing never reserve or decrement stock: only
 * the order service may do that, in its own transaction.
 */
@Service
public class ShoppingCart {
    /** Per-line quantity bound. It deliberately does not apply to cross-line SKU aggregates. */
    static final int MAX_QUANTITY = 99;
    static final int MIN_QUANTITY = 1;

    private final CartMapper cart;
    private final ObjectMapper json;
    private final CompatibilityService compatibility;

    ShoppingCart(CartMapper cart, ObjectMapper json, CompatibilityService compatibility) {
        this.cart = cart;
        this.json = json;
        this.compatibility = compatibility;
    }

    @Transactional(readOnly = true)
    public CartView view(long accountId) {
        var header = cart.cart(accountId);
        return header == null ? empty() : render(header, cart.lines(header.id()));
    }

    /**
     * Adds whole selling units of a real, sellable SKU, merging with the line already held for that
     * SKU. A request key makes a retried submission return the first answer instead of adding
     * twice; the cart row lock makes concurrent adds of different content accumulate rather than
     * lose one. Nothing here reserves stock, so the answer is a quotation, not a hold.
     */
    @Transactional(rollbackFor = Exception.class)
    public CartView add(long accountId, long productId, int quantity, String requestKey) {
        if (productId <= 0) invalid("商品 ID 必须是正整数。");
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            invalid("单次加入数量须为 " + MIN_QUANTITY + "～" + MAX_QUANTITY + " 的整数。");
        }
        String key = requestKey(requestKey);
        String fingerprint = fingerprint(accountId, key, productId, quantity);
        String token = UUID.randomUUID().toString();
        cart.ensureCart(accountId);
        cart.ensureRequest(accountId, key, fingerprint, token);
        var request = cart.lockRequest(accountId, key);
        if (!request.fingerprint().equals(fingerprint)) {
            throw new CartException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "该请求标识已用于不同的加购内容，请刷新购物车后重试。");
        }
        if (!request.creationToken().equals(token)) return replay(request.result());
        var header = cart.lockCart(accountId);
        var product = cart.product(productId);
        if (product == null) throw new CartException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在，无法加入购物车。");
        // A product that cannot be sold right now is not selectable, whether it is listed or not.
        if (!product.status().equals("ON_SALE")) {
            throw new CartException(HttpStatus.CONFLICT, "PRODUCT_NOT_SELECTABLE", "商品已下架，无法加入购物车。");
        }
        if (product.stock() <= 0) {
            throw new CartException(HttpStatus.CONFLICT, "PRODUCT_NOT_SELECTABLE", "商品暂时缺货，无法加入购物车。");
        }
        var existing = cart.lineForProduct(header.id(), productId);
        int current = existing == null ? 0 : existing.quantity();
        int merged = current + quantity;
        // The merged line itself must fit both the per-line bound and the current sellable quantity.
        if (merged > MAX_QUANTITY) {
            throw new CartException(HttpStatus.CONFLICT, "CART_QUANTITY_LIMIT",
                    "同一商品最多 " + MAX_QUANTITY + " 件，购物车已有 " + current + " 件。");
        }
        if (merged + cart.configurationQuantity(header.id(), productId) > product.stock()) {
            throw new CartException(HttpStatus.CONFLICT, "CART_STOCK_LIMIT",
                    "可售库存不足，当前仅剩 " + product.stock() + " 件，购物车已有 " + current + " 件。");
        }
        if (existing == null) cart.insertItem(header.id(), productId, merged);
        else cart.quantity(existing.id(), merged);
        if (cart.bumpRevision(header.id()) != 1) changeConflict();
        var view = render(new CartMapper.CartRow(header.id(), header.revision() + 1), cart.lines(header.id()));
        cart.completeRequest(accountId, key, encode(view));
        return view;
    }

    /** Sets an absolute quantity rather than a delta, so two clients cannot drift the same line. */
    @Transactional(rollbackFor = Exception.class)
    public CartView setQuantity(long accountId, long itemId, int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            invalid("数量须为 " + MIN_QUANTITY + "～" + MAX_QUANTITY + " 的整数。");
        }
        var header = ownedHeader(accountId);
        var line = ownedLine(header, itemId);
        var product = cart.product(line.productId());
        if (product == null) throw new CartException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "购物项商品不存在。");
        if (!product.status().equals("ON_SALE")) {
            throw new CartException(HttpStatus.CONFLICT, "PRODUCT_NOT_SELECTABLE", "商品已下架，请从购物车移除。");
        }
        if (product.stock() <= 0) {
            throw new CartException(HttpStatus.CONFLICT, "PRODUCT_NOT_SELECTABLE", "商品暂时缺货，请从购物车移除。");
        }
        if (quantity + cart.configurationQuantity(header.id(), line.productId()) > product.stock()) {
            throw new CartException(HttpStatus.CONFLICT, "CART_STOCK_LIMIT",
                    "可售库存不足，当前仅剩 " + product.stock() + " 件。");
        }
        long revision = header.revision();
        if (line.quantity() != quantity) {
            cart.quantity(line.id(), quantity);
            if (cart.bumpRevision(header.id()) != 1) changeConflict();
            revision++;
        }
        return render(new CartMapper.CartRow(header.id(), revision), cart.lines(header.id()));
    }

    /** Removes single-item lines only. */
    @Transactional(rollbackFor = Exception.class)
    public CartView remove(long accountId, long itemId) {
        var header = ownedHeader(accountId);
        var line = ownedLine(header, itemId);
        cart.deleteItem(line.id());
        if (cart.bumpRevision(header.id()) != 1) changeConflict();
        return render(new CartMapper.CartRow(header.id(), header.revision() + 1), cart.lines(header.id()));
    }

    @Transactional
    public CartView addConfiguration(long accountId, Map<HardwareCategory, Long> selections, Long graphics, int memoryQuantity) {
        boolean discrete = selections.containsKey(HardwareCategory.GPU);
        if (discrete ? graphics != null : graphics == null || !graphics.equals(selections.get(HardwareCategory.CPU))) {
            invalid("请选择独立显卡或当前 CPU 的核显。");
        }
        String signature = Arrays.stream(HardwareCategory.values())
                .map(category -> Long.toString(selections.getOrDefault(category, 0L)))
                .collect(Collectors.joining(","));
        // The original signature already represents one unit per category.
        if (memoryQuantity > 1) signature += ":" + memoryQuantity;
        cart.ensureCart(accountId);
        var header = cart.lockCart(accountId);
        // Lock all referenced products in the inventory lock order before any snapshot read.
        // Catalog writes lock the product before changing its parameters, price or availability.
        var products = new HashMap<Long, CartMapper.StockRow>();
        selections.values().stream().distinct().sorted().forEach(id -> products.put(id, cart.product(id)));
        Long existing = cart.configurationId(header.id(), signature);
        for (var entry : selections.entrySet()) {
            long productId = entry.getValue();
            var product = products.get(productId);
            if (product == null || !product.category().equals(entry.getKey().name())) {
                invalid("配置中有不存在或类别不符的商品，请重新选择。");
            }
            var single = cart.lineForProduct(header.id(), productId);
            int quantity = entry.getKey() == HardwareCategory.MEMORY ? memoryQuantity : 1;
            long demand = (existing == null ? quantity : 0) + (single == null ? 0 : single.quantity())
                    + cart.configurationQuantity(header.id(), productId);
            if (!product.status().equals("ON_SALE") || demand > product.stock()) {
                throw new CartException(HttpStatus.CONFLICT, "CART_STOCK_LIMIT",
                        "商品 " + product.sku() + " 当前不可售或可售库存不足，整套配置未加入购物车。");
            }
        }
        if (!compatibility.check(0, selections, graphics, memoryQuantity).corePassed()) {
            throw new CartException(HttpStatus.CONFLICT, "CONFIGURATION_NOT_COMPATIBLE",
                    "配置未通过核心兼容性检测，请检查配件后重试。");
        }
        if (existing != null) return render(header, cart.lines(header.id()));
        cart.insertConfiguration(header.id(), signature);
        long id = cart.configurationId(header.id(), signature);
        selections.forEach((category, productId) ->
                cart.insertConfigurationItem(id, productId, category == HardwareCategory.MEMORY ? memoryQuantity : 1));
        if (cart.bumpRevision(header.id()) != 1) changeConflict();
        return render(new CartMapper.CartRow(header.id(), header.revision() + 1), cart.lines(header.id()));
    }

    @Transactional
    public CartView removeConfiguration(long accountId, long id) {
        var header = ownedHeader(accountId);
        if (cart.deleteConfiguration(header.id(), id) != 1) {
            throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "配置组不存在。");
        }
        if (cart.bumpRevision(header.id()) != 1) changeConflict();
        return render(new CartMapper.CartRow(header.id(), header.revision() + 1), cart.lines(header.id()));
    }

    private CartMapper.CartRow ownedHeader(long accountId) {
        var header = cart.lockCart(accountId);
        if (header == null) throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "购物项不存在，请刷新购物车后重试。");
        return header;
    }

    private CartMapper.LineRow ownedLine(CartMapper.CartRow header, long itemId) {
        if (itemId <= 0) invalid("购物项 ID 必须是正整数。");
        var line = cart.line(header.id(), itemId);
        // Another owner's line answers as absent; ownership is never confirmed to a stranger.
        if (line == null || line.cartId() != header.id()) {
            throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "购物项不存在，请刷新购物车后重试。");
        }
        return line;
    }

    private CartView replay(String stored) {
        if (stored == null) {
            throw new CartException(HttpStatus.CONFLICT, "CART_REQUEST_INCOMPLETE", "上一次加购请求尚未完成，请刷新购物车后重试。");
        }
        try { return json.readValue(stored, CartView.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored cart answer is unreadable", exception); }
    }

    private String encode(CartView view) {
        try { return json.writeValueAsString(view); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot store cart answer", exception); }
    }

    private static String requestKey(String requestKey) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")) {
            invalid("请求标识须为 8～64 位字母、数字或 . _ : - 字符。");
        }
        return requestKey;
    }

    /**
     * Binds the key to the owner (the account is also the primary key), the operation and the exact
     * request content, so one key can never be replayed into a different addition.
     */
    private static String fingerprint(long accountId, String requestKey, long productId, int quantity) {
        String bound = accountId + "\n" + requestKey + "\nADD\n" + productId + "\n" + quantity;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bound.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private CartView render(CartMapper.CartRow header, List<CartMapper.LineView> rows) {
        var items = new ArrayList<Item>();
        var groupRows = cart.configurationLines(header.id());
        var demand = new HashMap<Long, Long>();
        rows.forEach(row -> demand.merge(row.productId(), (long) row.quantity(), Long::sum));
        groupRows.forEach(row -> demand.merge(row.productId(), (long) row.quantity(), Long::sum));
        var groupItems = new LinkedHashMap<Long, List<Item>>();
        for (var row : groupRows) {
            groupItems.computeIfAbsent(row.id(), ignored -> new ArrayList<>()).add(item(row, demand.get(row.productId())));
        }
        var configurations = new ArrayList<Configuration>();
        BigDecimal total = money(BigDecimal.ZERO);
        BigDecimal validTotal = money(BigDecimal.ZERO);
        long validCount = 0;
        for (var row : rows) {
            var item = item(row, demand.get(row.productId()));
            items.add(item);
            total = total.add(item.amount());
            if (item.state().equals("VALID")) { validTotal = validTotal.add(item.amount()); validCount++; }
        }
        for (var group : groupItems.entrySet()) {
            var members = List.copyOf(group.getValue());
            var amount = members.stream().map(Item::amount).reduce(money(BigDecimal.ZERO), BigDecimal::add);
            boolean valid = members.stream().allMatch(item -> item.state().equals("VALID"));
            configurations.add(new Configuration(group.getKey(), members, amount, valid));
            total = total.add(amount);
            if (valid) { validTotal = validTotal.add(amount); validCount++; }
        }
        return new CartView(header.revision(), List.copyOf(items), total, validTotal, validCount,
                items.size() + configurations.size(), List.copyOf(configurations));
    }

    private static Item item(CartMapper.LineView row, long demand) {
        String state = state(row, demand);
        return new Item(row.id(), row.productId(), row.sku(), row.name(), row.category(), row.categoryLabel(),
                row.brand(), row.model(), row.imageUrl(), row.quantity(), money(row.price()), row.stock(),
                state, stateMessage(state, demand, row.stock()), money(row.price().multiply(BigDecimal.valueOf(row.quantity()))));
    }

    /** Invalid lines are kept and labelled instead of being silently dropped. */
    private static String state(CartMapper.LineView row, long demand) {
        if (!row.status().equals("ON_SALE")) return "OFF_SALE";
        if (row.stock() <= 0) return "OUT_OF_STOCK";
        if (demand > row.stock()) return "INSUFFICIENT_STOCK";
        return "VALID";
    }

    private static String stateMessage(String state, long quantity, int stock) {
        return switch (state) {
            case "OFF_SALE" -> "商品已下架，请移除。";
            case "OUT_OF_STOCK" -> "商品暂时缺货，数量已保留。";
            case "INSUFFICIENT_STOCK" -> "可售库存不足：购物车 " + quantity + " 件，当前仅剩 " + stock + " 件。";
            default -> "按件计价，金额为服务端当前售价。";
        };
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY); }

    private static CartView empty() {
        return new CartView(0, List.of(), money(BigDecimal.ZERO), money(BigDecimal.ZERO), 0, 0, List.of());
    }

    private static void invalid(String message) {
        throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_CART_INPUT", message);
    }

    /** The cart row lock should make this unreachable; if it happens the transaction must not commit. */
    private static void changeConflict() {
        throw new CartException(HttpStatus.CONFLICT, "CART_CONFLICT", "购物车已被并发修改，请刷新后重试。");
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Item(long id, long productId, String sku, String name, String category, String categoryLabel,
                String brand, String model, String imageUrl, int quantity,
                @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price, int stock,
                String state, String message,
                @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}
    record Configuration(long id, List<Item> items,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, boolean valid) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record CartView(long revision, List<Item> items,
                    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
                    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal validTotal,
                    long validCount, long lineCount, List<Configuration> configurations) {
        CartView { if (configurations == null) configurations = List.of(); }
    }
}
