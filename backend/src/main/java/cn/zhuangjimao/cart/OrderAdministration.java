package cn.zhuangjimao.cart;

import cn.zhuangjimao.audit.AdministrationAudit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
class OrderAdministration {
    private final OrderMapper orders;
    private final SingleItemOrders lifecycle;
    private final AdministrationAudit audit;

    OrderAdministration(OrderMapper orders, SingleItemOrders lifecycle, AdministrationAudit audit) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page search(Query query) {
        if (query.page() < 1 || !Set.of(10, 20, 50).contains(query.pageSize())
                || query.orderNumber().length() > 40 || query.username().length() > 20
                || query.accountId() != null && query.accountId() <= 0
                || query.status() != null && !query.status().isEmpty()
                && !Set.of("PENDING_PAYMENT", "PAID", "COMPLETED", "CANCELLED").contains(query.status())) {
            throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUERY", "订单号、用户、状态或分页条件无效。");
        }
        var filter = new Query(like(query.orderNumber()), query.status(), like(query.username()), query.accountId(), query.page(), query.pageSize());
        long total = orders.adminCount(filter);
        var items = orders.adminNumbers(filter, (query.page() - 1L) * query.pageSize()).stream().map(this::detail).toList();
        return new Page(items, total, query.page(), query.pageSize());
    }

    @Transactional(readOnly = true)
    public Detail detail(String number) {
        var buyer = orders.buyer(number);
        if (buyer == null) throw new CartException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在。");
        return new Detail(buyer, lifecycle.view(buyer.id(), number));
    }

    public Detail complete(String number, String actor) throws Exception {
        var order = lifecycle.complete(number, actor);
        return new Detail(orders.buyer(number), order);
    }

    @Transactional(readOnly = true)
    public List<AdministrationAudit.Entry> history(String number) throws Exception {
        detail(number);
        return audit.history("ORDER", orders.auditId(number));
    }

    private static String like(String input) {
        String value = input.strip();
        return value.isEmpty() ? "" : "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    record Query(String orderNumber, String status, String username, Long accountId, int page, int pageSize) {}
    record Buyer(long id, String username, String nickname) {}
    record Detail(Buyer buyer, SingleItemOrders.View order) {}
    record Page(List<Detail> items, long total, int page, int pageSize) {}
}
