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
        if (itemIds.isEmpty() && configurationIds.isEmpty()) invalid("请至少选择一个本人购物车单品或完整配置组。");
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")) invalid("请求标识须为8～64位字母、数字或 . _ : - 字符。");
        if (quoteToken == null || !quoteToken.matches("[a-f0-9]{64}")) invalid("请先核对并确认当前报价。");
        // Preserve stored single-item request fingerprints while binding mixed requests to whole groups.
        String fingerprint = digest(accountId + "\nCREATE_ORDER\n" + revision + "\n" + new TreeSet<>(itemIds) + "\n" + quoteToken
                + (configurationIds.isEmpty() ? "" : "\nCONFIGURATIONS\n" + new TreeSet<>(configurationIds)));
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
        for (long id : new TreeSet<>(configurationIds)) {
            if (carts.ownedConfiguration(cart.id(), id) == null) {
                throw new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "选中的本人配置组不存在。");
            }
            for (var item : carts.configurationItems(id)) quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        String number = "O" + UUID.randomUUID().toString().replace("-", "");
        inventory.reserve(number, quantities, () -> {
            var quote = quotations.orderQuote(accountId, cart, itemIds, configurationIds, quoteToken);
            if (!quote.status().equals("READY")) throw new CartException(HttpStatus.CONFLICT, "PRICE_CONFIRMATION_REQUIRED", "报价或商品信息发生变化，请重新核验并确认后下单。");
            var created = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
            orders.create(number, accountId, quote.total(), created, created.plusMinutes(30));
            quote.items().forEach(item -> orders.snapshot(number, item, null));
            for (var group : quote.configurations()) {
                orders.configuration(number, group);
                group.items().forEach(item -> orders.snapshot(number, item, group.id()));
            }
        });
        for (long id : itemIds) {
            if (carts.deleteItem(id) != 1) throw new IllegalStateException("Selected cart item disappeared under its lock");
        }
        for (long id : configurationIds) {
            if (carts.deleteConfiguration(cart.id(), id) != 1) throw new IllegalStateException("Selected cart configuration disappeared under its lock");
        }
        if (carts.bumpRevision(cart.id()) != 1) throw new IllegalStateException("Cart disappeared under its lock");
        orders.remember(accountId, key, fingerprint, number);
        return view(accountId, number);
    }

    @Transactional(readOnly = true)
    public View view(long accountId, String number) {
        var order = orders.order(accountId, number);
        if (order == null) throw new CartException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在。");
        return snapshot(order);
    }

    private View snapshot(OrderMapper.Header order) {
        return new View(order.orderNumber(), order.status(), order.currency(), order.total(),
                order.createdAt().toInstant(ZoneOffset.UTC), order.paymentDeadline().toInstant(ZoneOffset.UTC),
                order.paidAt() == null ? null : order.paidAt().toInstant(ZoneOffset.UTC),
                order.cancelledAt() == null ? null : order.cancelledAt().toInstant(ZoneOffset.UTC), order.cancellationReason(),
                orders.items(order.orderNumber()), orders.configurations(order.orderNumber()));
    }

    /** Serialize lifecycle requests on the order; payment never changes inventory. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View pay(long accountId, String number) {
        var order = orders.lockOrder(accountId, number);
        if (order == null) throw new CartException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在。");
        if (order.status().equals("PAID")) return view(accountId, number);
        if (!order.status().equals("PENDING_PAYMENT")) {
            throw new CartException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "当前订单状态不允许支付。");
        }
        // Read time after waiting for the lock. Equality is already past the payment cutoff.
        var now = clock.instant();
        if (!now.isBefore(order.paymentDeadline().toInstant(ZoneOffset.UTC))) {
            throw new CartException(HttpStatus.CONFLICT, "ORDER_PAYMENT_EXPIRED", "订单已到支付截止时间，无法支付。");
        }
        var paidAt = LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        if (orders.pay(accountId, number, paidAt) != 1) throw new IllegalStateException("Order changed under its lock");
        return view(accountId, number);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View cancel(long accountId, String number) {
        var order = orders.lockOrder(accountId, number);
        if (order == null) throw new CartException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在。");
        if (order.status().equals("CANCELLED")) return view(accountId, number);
        if (!order.status().equals("PENDING_PAYMENT")) {
            throw new CartException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "当前订单状态不允许取消。");
        }
        cancelPending(order, "USER", clock.instant());
        return view(accountId, number);
    }

    private void cancelPending(OrderMapper.Header order, String reason, Instant now) {
        // Join the order transaction: a failure in either write leaves both retryable.
        inventory.release(order.reservationReference());
        if (orders.cancel(order.orderNumber(), LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC), reason) != 1) {
            throw new IllegalStateException("Order changed under its lock");
        }
    }

    /** Scanner uses the same lock and transaction as owner cancellation and payment. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void timeout(long accountId, String number) {
        var order = orders.lockOrder(accountId, number);
        if (order == null || !order.status().equals("PENDING_PAYMENT")) return;
        var now = clock.instant();
        if (now.isBefore(order.paymentDeadline().toInstant(ZoneOffset.UTC))) return;
        cancelPending(order, "TIMEOUT", now);
    }

    @Transactional(readOnly = true)
    public History history(long accountId, HistoryQuery query) {
        var states = List.of("PENDING_PAYMENT", "PAID", "COMPLETED", "CANCELLED");
        if (query.page() < 1 || !Set.of(10, 20, 50).contains(query.pageSize())
                || query.status() != null && !query.status().isEmpty() && !states.contains(query.status())
                || query.keyword() != null && query.keyword().length() > 100
                || query.startDate() != null && query.endDate() != null && query.startDate().isAfter(query.endDate())) {
            throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUERY", "订单筛选条件、日期范围或分页无效。");
        }
        String keyword = query.keyword() == null ? "" : query.keyword().strip();
        var filter = new HistoryQuery(query.page(), query.pageSize(), query.status(), keyword.isEmpty() ? ""
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%", query.startDate(), query.endDate());
        var counts = new LinkedHashMap<String, Long>();
        counts.put("ALL", 0L);
        states.forEach(state -> counts.put(state, 0L));
        for (var count : orders.counts(accountId, filter)) {
            counts.put(count.status(), count.count());
            counts.compute("ALL", (key, total) -> total + count.count());
        }
        var items = orders.history(accountId, filter, (query.page() - 1L) * query.pageSize()).stream().map(this::snapshot).toList();
        return new History(items, query.page(), query.pageSize(), counts.get(query.status() == null || query.status().isEmpty() ? "ALL" : query.status()), counts);
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void invalid(String message) { throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_INPUT", message); }

    record View(String orderNumber, String status, String currency, @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal total,
                Instant createdAt, Instant paymentDeadline, @JsonInclude(JsonInclude.Include.ALWAYS) Instant paidAt,
                @JsonInclude(JsonInclude.Include.ALWAYS) Instant cancelledAt, @JsonInclude(JsonInclude.Include.ALWAYS) String cancellationReason,
                List<Item> items, List<Configuration> configurations) {}
    record Configuration(long id, @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount) {}
    record HistoryQuery(int page, int pageSize, String status, String keyword, LocalDate startDate, LocalDate endDate) {
        public LocalDateTime from() { return startDate == null ? null : utc(startDate); }
        public LocalDateTime until() { return endDate == null ? null : utc(endDate.plusDays(1)); }
        private static LocalDateTime utc(LocalDate date) {
            return LocalDateTime.ofInstant(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneOffset.UTC);
        }
    }
    record History(List<View> items, int page, int pageSize, long total, Map<String, Long> counts) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Item(long productId, String sku, String name, String model, String category,
                @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal price, int quantity,
                @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount, Long configurationId) {}
}
