package cn.zhuangjimao.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface AuditMapper {
    @Insert("""
        INSERT INTO admin_audit(actor_id,actor_username,object_type,object_id,action,before_value,after_value)
        SELECT id,username,#{objectType},#{objectId},#{action},#{before},#{after} FROM account WHERE username=#{actor}
        """)
    int insert(String actor, String objectType, long objectId, String action, String before, String after);
}
