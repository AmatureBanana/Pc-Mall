package cn.zhuangjimao.cart;

import org.springframework.http.HttpStatus;

class CartException extends RuntimeException {
    final HttpStatus status;
    final String code;

    CartException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
