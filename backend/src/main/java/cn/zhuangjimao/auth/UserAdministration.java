package cn.zhuangjimao.auth;

import cn.zhuangjimao.audit.AdministrationAudit;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
class UserAdministration {
    private final UserAdministrationMapper users;
    private final AdministrationAudit audit;

    UserAdministration(UserAdministrationMapper users, AdministrationAudit audit) { this.users = users; this.audit = audit; }

    @Transactional(readOnly = true)
    public Page search(String username, String status, int page, int size) {
        if (username.length() > 20 || page < 1 || size < 1 || size > 100
                || status != null && !Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw AccountInputs.invalid("用户名、状态或分页条件无效。");
        }
        String query = username.strip();
        return new Page(users.search(query, status, size, (long) (page - 1) * size), users.count(query, status), page, size);
    }

    @Transactional(readOnly = true)
    public Detail detail(long id) {
        if (id <= 0) throw AccountInputs.invalid("用户 ID 必须是正整数。");
        var result = users.detail(id);
        if (result == null) throw new AuthException(404, "USER_NOT_FOUND", "普通用户不存在。");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail status(long id, JsonNode input, String actor) throws Exception {
        if (id <= 0) throw AccountInputs.invalid("用户 ID 必须是正整数。");
        if (input == null || !input.isObject() || input.size() != 2 || !input.path("status").isTextual()
                || !Set.of("ACTIVE", "DISABLED").contains(input.path("status").asText())
                || !input.path("revision").isIntegralNumber() || !input.path("revision").canConvertToLong()
                || input.path("revision").asLong() < 0) {
            throw AccountInputs.invalid("仅允许提交 ACTIVE/DISABLED 状态及非负整数版本。");
        }
        var before = users.lock(id);
        if (before == null) throw new AuthException(404, "USER_NOT_FOUND", "普通用户不存在。");
        if (!before.role().equals("USER")) throw new AuthException(403, "ADMIN_PROTECTED", "此入口不能修改管理员账户。");
        if (before.revision() != input.path("revision").asLong()) {
            throw new AuthException(409, "USER_CONFLICT", "用户状态已被其他操作修改，请重新载入后再试。");
        }
        String status = input.path("status").asText();
        if (before.status().equals(status)) return before;
        users.status(id, status);
        var after = detail(id);
        audit.record(actor, "USER", id, "STATUS", before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<AdministrationAudit.Entry> history(long id) throws Exception {
        detail(id);
        return audit.history("USER", id);
    }

    record Detail(long id, String username, String nickname, String role, String status, long revision,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record Page(List<Detail> items, long total, int page, int size) {}
}
