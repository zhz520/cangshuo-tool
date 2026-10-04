package com.cangshuo.toolbox.common.exception;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ApiResponse<Void> response = ApiResponse.failure(ApiError.forStatus(status.value()),
                TraceIdFilter.traceId(servletRequest));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(response, responseHeaders, status);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessResourceException.class,
            QueryTimeoutException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnavailableDataSource(Exception exception,
                                                                        HttpServletRequest request) {
        LOG.warn("Request data source unavailable; exceptionType={}", exception.getClass().getName());
        return ResponseEntity.status(ApiError.SERVICE_UNAVAILABLE.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ApiError.SERVICE_UNAVAILABLE, TraceIdFilter.traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception,
                                                                       HttpServletRequest request) {
        LOG.error("Unhandled request error; exceptionType={}", exception.getClass().getName());
        return ResponseEntity.status(ApiError.INTERNAL_ERROR.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ApiError.INTERNAL_ERROR, TraceIdFilter.traceId(request)));
    }
}
