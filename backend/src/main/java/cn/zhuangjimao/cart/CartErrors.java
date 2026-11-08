package cn.zhuangjimao.cart;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Scoped to this package so the catalog's catch-all cannot turn a cart rule violation into a
 * generic 500, and ordered ahead of it for the shared persistence failure contract. Every answer,
 * including a failure, is uncacheable because it depends on the caller's session and live data.
 */
@RestControllerAdvice(basePackages = "cn.zhuangjimao.cart")
@Order(Ordered.HIGHEST_PRECEDENCE)
class CartErrors {
    @ExceptionHandler(CartException.class)
    ResponseEntity<ApiError> cart(CartException exception) {
        return answer(exception.status, new ApiError(exception.code, exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed() {
        return answer(HttpStatus.BAD_REQUEST, new ApiError("INVALID_CART_INPUT", "请求格式、字段类型或购物项标识无效。"));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ApiError> unavailable() {
        return answer(HttpStatus.SERVICE_UNAVAILABLE, new ApiError("CART_UNAVAILABLE", "购物车服务暂时不可用，请稍后重试。"));
    }

    private static ResponseEntity<ApiError> answer(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(error);
    }

    record ApiError(String code, String message) {}
}
