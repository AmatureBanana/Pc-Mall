package cn.zhuangjimao.catalog;

import org.springframework.http.HttpStatus;

class CatalogException extends RuntimeException {
    final HttpStatus status;
    final String code;

    CatalogException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
