package cn.zhuangjimao.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class OrderTimeouts {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeouts.class);
    private final OrderMapper orders;
    private final SingleItemOrders lifecycle;
    private final Clock clock;

    OrderTimeouts(OrderMapper orders, SingleItemOrders lifecycle, Clock clock) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *")
    public void scan() {
        var cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String after = "";
        while (true) {
            var batch = orders.expired(cutoff, after);
            if (batch.isEmpty()) return;
            for (var order : batch) {
                try { lifecycle.timeout(order.accountId(), order.orderNumber()); }
                catch (RuntimeException failure) {
                    log.warn("Timeout cancellation failed for order {}; a later scan will retry", order.orderNumber(), failure);
                }
            }
            // Advance past failed rows too, so one failure cannot starve later orders.
            after = batch.getLast().orderNumber();
        }
    }
}

/** Tests invoke the real scanner explicitly with a controlled Clock. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
class OrderTimeoutScheduling {}
