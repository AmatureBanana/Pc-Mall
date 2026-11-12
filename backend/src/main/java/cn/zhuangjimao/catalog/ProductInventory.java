package cn.zhuangjimao.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Available stock only. Order services join these transactions and enforce their own lifecycle. */
@Service
public class ProductInventory {
    private final InventoryMapper store;
    private final ObjectMapper json;
    public ProductInventory(InventoryMapper store, ObjectMapper json) { this.store = store; this.json = json; }

    @Transactional
    public Reservation reserve(String reference, Map<Long,Integer> quantities) {
        return reserve(reference, quantities, () -> {});
    }

    /** Final order validation runs under every product write lock, before any stock changes. */
    @Transactional
    public Reservation reserve(String reference, Map<Long,Integer> quantities, Runnable validate) {
        reference(reference);
        if (quantities == null || quantities.isEmpty()) invalid("预占明细不能为空。");
        var sorted = new TreeMap<Long,Integer>();
        quantities.forEach((id, quantity) -> {
            if (id == null || id <= 0 || quantity == null || quantity <= 0) invalid("预占商品和数量须为正整数。");
            sorted.put(id, quantity);
        });
        // All inventory operations lock reservation first (when applicable), then products by ascending ID.
        // MySQL affected-row counts vary with found-rows mode. A per-attempt token identifies
        // creation without scanning/locking an empty item range before taking product locks.
        String token = UUID.randomUUID().toString();
        store.ensureReservation(reference, encode(sorted), token);
        var header = store.lockReservation(reference);
        var previous = decode(header.quantities());
        if (!header.state().equals("RESERVED") || !previous.equals(sorted)) conflict("预占标识已用于其他内容或已释放。");
        if (!header.creationToken().equals(token)) {
            return new Reservation(header.state(), previous);
        }
        var balances = lockProducts(sorted.keySet());
        validate.run();
        for (var balance : balances) {
            int quantity = sorted.get(balance.id());
            if (!balance.status().equals("ON_SALE") || balance.stock() < quantity) conflict("商品已下架或可售库存不足。");
            change(balance, -quantity, quantity);
            store.item(reference, balance.id(), quantity);
        }
        return new Reservation("RESERVED", Map.copyOf(sorted));
    }

    @Transactional
    public Reservation release(String reference) {
        reference(reference);
        var header = store.lockReservation(reference);
        if (header == null) throw new CatalogException(HttpStatus.NOT_FOUND,"RESERVATION_NOT_FOUND","预占记录不存在。");
        var quantities = decode(header.quantities());
        if (header.state().equals("RELEASED")) return new Reservation(header.state(), quantities);
        for (var balance : lockProducts(quantities.keySet())) {
            int quantity = quantities.get(balance.id());
            change(balance, quantity, -quantity);
        }
        if (store.release(reference) != 1) conflict("预占状态已变化。");
        return new Reservation("RELEASED", quantities);
    }

    @Transactional(readOnly = true)
    public Reservation reservation(String reference) {
        reference(reference);
        var header = store.reservation(reference);
        return header == null ? null : new Reservation(header.state(), decode(header.quantities()));
    }
    private String encode(Map<Long,Integer> quantities) {
        try { return json.writeValueAsString(quantities); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode reservation", e); }
    }
    private Map<Long,Integer> decode(String quantities) {
        try { return Map.copyOf(json.readValue(quantities, new TypeReference<TreeMap<Long,Integer>>() {})); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored reservation", e); }
    }
    private List<InventoryMapper.Balance> lockProducts(Set<Long> ids) {
        var balances = new ArrayList<InventoryMapper.Balance>();
        for (long id : new TreeSet<>(ids)) {
            var balance = store.lock(id);
            if (balance == null) throw new CatalogException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","商品不存在。");
            balances.add(balance);
        }
        return balances;
    }
    private void change(InventoryMapper.Balance balance, int available, int reserved) {
        if (store.change(balance.id(), balance.revision(), available, reserved) != 1) conflict("库存已变化或超出范围。");
    }
    private static void reference(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) invalid("预占标识格式无效。");
    }
    private static void invalid(String message) { throw new CatalogException(HttpStatus.BAD_REQUEST,"INVALID_RESERVATION",message); }
    private static void conflict(String message) { throw new CatalogException(HttpStatus.CONFLICT,"STOCK_CONFLICT",message); }
    public record Reservation(String state, Map<Long,Integer> quantities) {}
}
