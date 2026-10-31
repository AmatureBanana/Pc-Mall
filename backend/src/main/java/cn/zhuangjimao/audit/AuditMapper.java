package cn.zhuangjimao.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AuditMapper {
    @Insert("""
        INSERT INTO admin_audit(actor_id,actor_username,object_type,object_id,action,before_value,after_value)
        SELECT id,username,#{objectType},#{objectId},#{action},#{before},#{after} FROM account WHERE username=#{actor}
        """)
    int insert(String actor, String objectType, long objectId, String action, String before, String after);

    @Select("""
        SELECT id,actor_id,actor_username,object_type,object_id,action,occurred_at,before_value,after_value
        FROM admin_audit WHERE object_type=#{objectType} AND object_id=#{objectId} ORDER BY id DESC LIMIT 100
        """)
    java.util.List<Entry> history(String objectType, long objectId);

    record Entry(long id, long actorId, String actorUsername, String objectType, long objectId, String action,
                 java.time.LocalDateTime occurredAt, String beforeValue, String afterValue) {}
}
