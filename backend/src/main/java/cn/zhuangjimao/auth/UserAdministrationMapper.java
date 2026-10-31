package cn.zhuangjimao.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
interface UserAdministrationMapper {
    String SUMMARY = "SELECT id,username,nickname,role,status,administration_revision AS revision,created_at,updated_at FROM account ";
    String FILTER = "WHERE role='USER' AND (#{status} IS NULL OR status=#{status}) AND LOCATE(#{username},username)>0 ";

    @Select(SUMMARY + FILTER + "ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<UserAdministration.Detail> search(String username, String status, int size, long offset);

    @Select("SELECT COUNT(*) FROM account " + FILTER)
    long count(String username, String status);

    @Select(SUMMARY + "WHERE id=#{id} AND role='USER'")
    UserAdministration.Detail detail(long id);

    @Select(SUMMARY + "WHERE id=#{id} FOR UPDATE")
    UserAdministration.Detail lock(long id);

    @Update("""
        UPDATE account SET status=#{status},administration_revision=administration_revision+1,
        credential_version=credential_version+1 WHERE id=#{id} AND role='USER'
        """)
    void status(long id, String status);
}
