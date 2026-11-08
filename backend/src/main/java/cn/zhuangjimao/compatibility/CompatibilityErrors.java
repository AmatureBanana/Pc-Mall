package cn.zhuangjimao.compatibility;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to this package so the catalog's own error contract elsewhere is untouched; ordered ahead
 * of the catalog catch-all so a bad diagnostic request stays a structured 400.
 */
@RestControllerAdvice(basePackages = "cn.zhuangjimao.compatibility")
@Order(Ordered.HIGHEST_PRECEDENCE)
class CompatibilityErrors {
    @ExceptionHandler(InvalidConfiguration.class)
    ResponseEntity<ApiError> invalid(InvalidConfiguration exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("INVALID_CONFIGURATION", exception.getMessage()));
    }

    record ApiError(String code, String message) {}
}
