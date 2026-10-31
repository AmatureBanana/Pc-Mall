package cn.zhuangjimao.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(CatalogException.class)
    ResponseEntity<ApiError> catalog(CatalogException exception) {
        return ResponseEntity.status(exception.status).body(new ApiError(exception.code, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidArgument(MethodArgumentTypeMismatchException exception) {
        boolean id = exception.getName().equals("id");
        return ResponseEntity.badRequest().body(new ApiError(id ? "INVALID_PRODUCT_ID" : "INVALID_QUERY",
                id ? "商品 ID 必须是有效正整数。" : "请选择有效的硬件类别。"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseUnavailable(DataAccessException exception) {
        log.error("Product database request failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("CATALOG_UNAVAILABLE", "商品服务暂时不可用，请稍后重试。"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unexpected API failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务异常，请稍后重试。"));
    }

    record ApiError(String code, String message) {}

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformed() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PRODUCT", "请求格式、字段类型或必要条件无效。"));
    }
}
