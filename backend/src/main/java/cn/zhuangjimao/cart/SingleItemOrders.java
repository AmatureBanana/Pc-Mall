package cn.zhuangjimao.cart;

import cn.zhuangjimao.catalog.ProductInventory;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Cart lock → request → reservation → ascending product IDs, all in one MySQL transaction. */
@Service
public class SingleItemOrders {
    private final CartMapper carts;
    private final ShoppingCart quotations;
    private final ProductInventory inventory;
    private final OrderMapper orders;
    private final Clock clock;

    SingleItemOrders(CartMapper carts, ShoppingCart quotations, ProductInventory inventory, OrderMapper orders, Clock clock) {
        this.carts = carts;
        this.quotations = quotations;
        this.inventory = inventory;
        this.orders = orders;
        this.clock = clock;
    }

    // Every mutable input is protected by a row lock. READ_COMMITTED avoids locking an absent
    // idempotency key's index gap across unrelated owners, while reads after product locks are current.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View create(long accountId, long revision, Set<Long> itemIds, Set<Long> configurationIds, String quoteToken, String key) {
        if (!configurationIds.isEmpty()) throw new CartException(HttpStatus.CONFLICT, "MIXED_ORDER_NOT_SUPPORTED", "配置组下单尚未开放，请仅选择单品。");
        if (itemIds.isEmpty()) invalid("请至少选择一个本人购物车单品。");
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")) invalid("请求标识须为8～64位字母、数字或 . _ : - 字符。");
        if (quoteToken == null || !quoteToken.matches("[a-f0-9]{64}")) invalid("请先核对并确认当前报价。");
        String fingerprint = digest(accountId + "\nCREATE_ORDER\n" + revision + "\n" + new TreeSet<>(itemIds) + "\n" + quoteToken);
        var cart = carts.lockCart(accountId);
        if (cart == null) throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "本人购物车没有选中项。");
        var previous = orders.request(accountId, key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new CartException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同下单内容。");
            return view(accountId, previous.orderNumber());
        }
        if (cart.revision() != revision) throw new CartException(HttpStatus.CONFLICT, "CART_CONFLICT", "购物车已变化，请刷新并重新确认报价。");
        var quantities = new TreeMap<Long,Integer>();
        for (long id : new TreeSet<>(itemIds)) {
            var line = carts.line(cart.id(), id);
            if (line == null) throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "选中的本人单品不存在。");
            if (line.quantity() < 1 || line.quantity() > 99) invalid("单品数量须为1～99。");
            quantities.merge(line.productId(), line.quantity(), Math::addExact);
        }
        String number = "O" + UUID.randomUUID().toString().replace("-", "");
        inventory.reserve(number, quantities, () -> {
            var quote = quotations.singleOrderQuote(accountId, cart, itemIds, quoteToken);
            if (!quote.status().equals("READY")) throw new CartException(HttpStatus.CONFLICT, "PRICE_CONFIRMATION_REQUIRED", "报价或商品信息发生变化，请重新核验并确认后下单。");
            var created = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
            orders.create(number, accountId, quote.total(), created, created.plusMinutes(30));
            quote.items().forEach(item -> orders.snapshot(number, item));
        });
        for (long id : itemIds) {
            if (carts.deleteItem(id) != 1) throw new IllegalStateException("Selected cart item disappeared under its lock");
        }
        if (carts.bumpRevision(cart.id()) != 1) throw new IllegalStateException("Cart disappeared under its lock");
        orders.remember(accountId, key, fingerprint, number);
        return view(accountId, number);
    }

    @Transactional(readOnly = true)
    public View view(long accountId, String number) {
        var order = orders.order(accountId, number);
        if (order == null) throw new CartException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在。");
        return new View(order.orderNumber(), order.status(), order.currency(), order.total(),
                order.createdAt().toInstant(ZoneOffset.UTC), order.paymentDeadline().toInstant(ZoneOffset.UTC), orders.items(number));
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void invalid(String message) { throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_INPUT", message); }

    record View(String orderNumber, String status, String currency, @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal total,
                Instant createdAt, Instant paymentDeadline, List<Item> items) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Item(long productId, String sku, String name, String model, String category,
                @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal price, int quantity,
                @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount, Long configurationId) {}
}
