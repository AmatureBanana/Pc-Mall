package cn.zhuangjimao.auth;

import java.nio.charset.StandardCharsets;

final class AccountInputs {
    private AccountInputs() {}

    static void username(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{4,20}")) {
            throw invalid("用户名须为 4～20 位英文字母、数字或下划线。");
        }
    }

    static void password(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.codePointCount(0, password.length()) > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)) {
            throw invalid("密码须为 8～64 个字符，且 UTF-8 编码不超过 72 字节。");
        }
    }

    static String nickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) return username;
        if (nickname.codePointCount(0, nickname.length()) > 30
                || nickname.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)) {
            throw invalid("昵称最多 30 个字符。");
        }
        return nickname;
    }

    static AuthException invalid(String message) { return new AuthException(400, "INVALID_INPUT", message); }
}
