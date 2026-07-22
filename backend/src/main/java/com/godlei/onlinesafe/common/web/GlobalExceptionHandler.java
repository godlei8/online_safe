package com.godlei.onlinesafe.common.web;

import com.godlei.onlinesafe.admin.application.InvalidInvitationOperationException;
import com.godlei.onlinesafe.admin.application.InvalidUserOperationException;
import com.godlei.onlinesafe.admin.application.InvitationNotFoundException;
import com.godlei.onlinesafe.admin.application.UserNotFoundException;
import com.godlei.onlinesafe.auth.application.InvalidRegistrationException;
import com.godlei.onlinesafe.auth.application.PasswordResetException;
import com.godlei.onlinesafe.auth.application.RegistrationConflictException;
import com.godlei.onlinesafe.vault.application.InvalidVaultEnvelopeException;
import com.godlei.onlinesafe.vault.application.PrivateTemplateNotFoundException;
import com.godlei.onlinesafe.vault.application.VaultItemNotFoundException;
import com.godlei.onlinesafe.vault.application.VaultRevisionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request, fields);
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    ResponseEntity<ApiError> handleInvalidRegistration(
            InvalidRegistrationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(RegistrationConflictException.class)
    ResponseEntity<ApiError> handleRegistrationConflict(
            RegistrationConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS", exception.getMessage(), request, null);
    }

    @ExceptionHandler(PasswordResetException.class)
    ResponseEntity<ApiError> handlePasswordReset(PasswordResetException exception, HttpServletRequest request) {
        HttpStatus status = "PASSWORD_RESET_RATE_LIMITED".equals(exception.getCode())
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_REQUEST;
        return response(status, exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    ResponseEntity<ApiError> handleInvitationNotFound(
            InvitationNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidInvitationOperationException.class)
    ResponseEntity<ApiError> handleInvalidInvitation(
            InvalidInvitationOperationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidUserOperationException.class)
    ResponseEntity<ApiError> handleInvalidUser(
            InvalidUserOperationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(VaultItemNotFoundException.class)
    ResponseEntity<ApiError> handleVaultItemNotFound(
            VaultItemNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "VAULT_ITEM_NOT_FOUND", exception.getMessage(), request, null);
    }

    @ExceptionHandler(PrivateTemplateNotFoundException.class)
    ResponseEntity<ApiError> handlePrivateTemplateNotFound(
            PrivateTemplateNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "PRIVATE_TEMPLATE_NOT_FOUND", exception.getMessage(), request, null);
    }

    @ExceptionHandler(VaultRevisionConflictException.class)
    ResponseEntity<ApiError> handleVaultRevisionConflict(
            VaultRevisionConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "VAULT_REVISION_CONFLICT", exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidVaultEnvelopeException.class)
    ResponseEntity<ApiError> handleInvalidVaultEnvelope(
            InvalidVaultEnvelopeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "账号或密码错误", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure at {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系统暂时无法处理请求", request, null);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fields
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                fields
        ));
    }
}
