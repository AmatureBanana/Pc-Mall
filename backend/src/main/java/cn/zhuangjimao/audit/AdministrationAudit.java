package cn.zhuangjimao.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministrationAudit {
    private final AuditMapper records;
    private final ObjectMapper json;

    public AdministrationAudit(AuditMapper records, ObjectMapper json) {
        this.records = records;
        this.json = json;
    }

    /** Refuses use outside the caller's business transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String objectType, long objectId, String action, Object before, Object after)
            throws JsonProcessingException {
        int inserted = records.insert(actor, objectType, objectId, action, json.writeValueAsString(before), json.writeValueAsString(after));
        if (inserted != 1) throw new IllegalStateException("Audit actor is no longer available");
    }

    @Transactional(readOnly = true)
    public java.util.List<Entry> history(String objectType, long objectId) throws JsonProcessingException {
        var result = new java.util.ArrayList<Entry>();
        for (var entry : records.history(objectType, objectId)) {
            result.add(new Entry(entry.id(), entry.actorId(), entry.actorUsername(), entry.objectType(), entry.objectId(),
                    entry.action(), entry.occurredAt(), json.readTree(entry.beforeValue()), json.readTree(entry.afterValue())));
        }
        return result;
    }

    public record Entry(long id, long actorId, String actorUsername, String objectType, long objectId, String action,
                        java.time.LocalDateTime occurredAt, JsonNode beforeValue, JsonNode afterValue) {}
}
