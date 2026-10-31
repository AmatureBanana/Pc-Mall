package cn.zhuangjimao.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

record Account(long id, String username, @JsonIgnore String passwordHash, String nickname, String role, String status) {
    Summary summary() { return new Summary(id, username, nickname, role); }
    record Summary(long id, String username, String nickname, String role) {}
}
