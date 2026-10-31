package cn.zhuangjimao.auth;

class AuthException extends RuntimeException {
    final int status;
    final String code;
    AuthException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
