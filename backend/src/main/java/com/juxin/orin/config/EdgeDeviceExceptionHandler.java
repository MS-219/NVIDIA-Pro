package com.juxin.orin.config;

import com.juxin.orin.common.Result;
import com.juxin.orin.controller.EdgeDeviceController;
import com.juxin.orin.exception.EdgeDeviceApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EdgeDeviceController.class)
public class EdgeDeviceExceptionHandler {

    @ExceptionHandler(EdgeDeviceApiException.class)
    public ResponseEntity<Result<Void>> handleEdgeDeviceApiException(EdgeDeviceApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus().value(), exception.getMessage()));
    }
}
