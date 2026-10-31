package cn.zhuangjimao.auth;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(basePackageClasses = AuthController.class)
class AuthErrors {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<Error> authentication(AuthException error) {
        return ResponseEntity.status(error.status).body(new Error(error.code, error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> malformed() {
        return ResponseEntity.badRequest().body(new Error("INVALID_INPUT", "请求格式或字段类型无效。"));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<Error> invalidArgument() {
        return ResponseEntity.badRequest().body(new Error("INVALID_INPUT", "用户 ID、分页或筛选条件无效。"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Error> unavailable() {
        return ResponseEntity.status(503).body(new Error("AUTH_UNAVAILABLE", "账户服务暂时不可用，请稍后重试。"));
    }

    record Error(String code, String message) {}
}
