package cn.zhuangjimao.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
class AdminController {
    @GetMapping("/api/admin/overview")
    Map<String, Object> overview() {
        return Map.of("data", List.of(
                new Module("products", "商品管理", "商品资料、参数与上下架", "商品维护已开放。"),
                new Module("users", "用户管理", "普通用户状态管理", "用户管理功能即将开放。"),
                new Module("orders", "订单管理", "订单查询与完成", "订单管理功能即将开放。")));
    }
    record Module(String code, String title, String description, String availability) {}
}
