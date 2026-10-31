package cn.zhuangjimao.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AccountMapper {
    @Select("SELECT id, username, password_hash, nickname, role, status FROM account WHERE username = #{username}")
    Account byUsername(String username);

    @Select("SELECT id, username, password_hash, nickname, role, status FROM account WHERE id = #{id}")
    Account byId(long id);

    @Select("SELECT id FROM account_initialization_lock WHERE id = 1 FOR UPDATE")
    int lockInitialization();

    @Select("SELECT COUNT(*) FROM account WHERE role = 'ADMIN'")
    int administrators();

    @Insert("INSERT INTO account(username, password_hash, nickname, role) VALUES(#{username}, #{passwordHash}, #{nickname}, #{role})")
    void insert(String username, String passwordHash, String nickname, String role);
}
