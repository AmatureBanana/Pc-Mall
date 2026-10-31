package cn.zhuangjimao.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
