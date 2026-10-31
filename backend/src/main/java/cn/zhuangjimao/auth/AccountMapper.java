package cn.zhuangjimao.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface AccountMapper {
    @Select("SELECT id, username, password_hash, nickname, role, status, credential_version FROM account WHERE username = #{username}")
    Account byUsername(String username);

    @Select("SELECT id, username, password_hash, nickname, role, status, credential_version FROM account WHERE id = #{id}")
    Account byId(long id);

    @Select("SELECT id, username, password_hash, nickname, role, status, credential_version FROM account WHERE id = #{id} FOR UPDATE")
    Account lockById(long id);

    @Update("UPDATE account SET nickname = #{nickname} WHERE id = #{id}")
    void updateNickname(long id, String nickname);

    @Update("UPDATE account SET password_hash = #{passwordHash}, credential_version = credential_version + 1 WHERE id = #{id}")
    void updatePassword(long id, String passwordHash);

    @Select("SELECT id FROM account_initialization_lock WHERE id = 1 FOR UPDATE")
    int lockInitialization();

    @Select("SELECT COUNT(*) FROM account WHERE role = 'ADMIN'")
    int administrators();

    @Insert("INSERT INTO account(username, password_hash, nickname, role) VALUES(#{username}, #{passwordHash}, #{nickname}, #{role})")
    void insert(String username, String passwordHash, String nickname, String role);
}
