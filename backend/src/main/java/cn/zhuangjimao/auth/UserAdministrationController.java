package cn.zhuangjimao.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
class UserAdministrationController {
    private final UserAdministration users;
    UserAdministrationController(UserAdministration users) { this.users = users; }

    @GetMapping
    Map<String, Object> search(@RequestParam(defaultValue = "") String username, @RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Map.of("data", users.search(username, status, page, size));
    }

    @GetMapping("/{id}")
    Map<String, Object> detail(@PathVariable long id) { return Map.of("data", users.detail(id)); }

    @PostMapping("/{id}/status")
    Map<String, Object> status(@PathVariable long id, @RequestBody JsonNode input, Authentication actor) throws Exception {
        return Map.of("data", users.status(id, input, actor.getName()));
    }

    @GetMapping("/{id}/audit")
    Map<String, Object> history(@PathVariable long id) throws Exception { return Map.of("data", users.history(id)); }
}
