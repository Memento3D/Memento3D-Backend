package com.memeto.team.memeto3d.exception;

import com.memeto.team.memeto3d.dto.ErrorResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("Test handleEmailAlreadyExists -> 409 Conflict")
    void testHandleEmailAlreadyExists() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("Email exists");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleEmailAlreadyExists(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Email exists", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("Test handleBadCredentials -> 401 Unauthorized")
    void testHandleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadCredentials(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Invalid credentials", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Test handleUsernameNotFound -> 404 Not Found")
    void testHandleUsernameNotFound() {
        UsernameNotFoundException ex = new UsernameNotFoundException("User not found");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUsernameNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("User not found", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Test handleResourceNotFound -> 404 Not Found")
    void testHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Resource not found", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Test handleValidationException with field errors -> 400 Bad Request")
    void testHandleValidationExceptionWithErrors() throws NoSuchMethodException {
        Method method = this.getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "Email is invalid"));
        bindingResult.addError(new FieldError("target", "password", "Password cannot be empty"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertNotNull(response.getBody().getValidationErrors());
        assertEquals("Email is invalid", response.getBody().getValidationErrors().get("email"));
        assertEquals("Password cannot be empty", response.getBody().getValidationErrors().get("password"));
    }

    @Test
    @DisplayName("Test handleValidationException with empty field errors -> 400 Bad Request fallback message")
    void testHandleValidationExceptionEmptyErrors() throws NoSuchMethodException {
        Method method = this.getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Validation failed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleJwtExceptions for ExpiredJwtException -> 401")
    void testHandleJwtExceptionsExpired() {
        ExpiredJwtException ex = new ExpiredJwtException(mock(Header.class), null, "Token expired");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleJwtExceptions(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("JWT token has expired", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleJwtExceptions for InvalidTokenException -> 401")
    void testHandleJwtExceptionsInvalidToken() {
        InvalidTokenException ex = new InvalidTokenException("Custom invalid token message");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleJwtExceptions(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Custom invalid token message", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleJwtExceptions for MalformedJwtException & SignatureException -> 401")
    void testHandleJwtExceptionsMalformedAndSignature() {
        MalformedJwtException malformed = new MalformedJwtException("Malformed");
        ResponseEntity<ErrorResponse> response1 = exceptionHandler.handleJwtExceptions(malformed, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response1.getStatusCode());
        assertEquals("JWT token is invalid or expired", response1.getBody().getMessage());

        SignatureException signature = new SignatureException("Signature failed");
        ResponseEntity<ErrorResponse> response2 = exceptionHandler.handleJwtExceptions(signature, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response2.getStatusCode());
        assertEquals("JWT token is invalid or expired", response2.getBody().getMessage());

        JwtException genericJwt = new JwtException("Generic JWT error");
        ResponseEntity<ErrorResponse> response3 = exceptionHandler.handleJwtExceptions(genericJwt, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response3.getStatusCode());
        assertEquals("JWT token is invalid or expired", response3.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleAuthenticationException with message and null message -> 401")
    void testHandleAuthenticationException() {
        InsufficientAuthenticationException ex1 = new InsufficientAuthenticationException("Access token required");
        ResponseEntity<ErrorResponse> response1 = exceptionHandler.handleAuthenticationException(ex1, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response1.getStatusCode());
        assertEquals("Access token required", response1.getBody().getMessage());

        InsufficientAuthenticationException ex2 = new InsufficientAuthenticationException(null);
        ResponseEntity<ErrorResponse> response2 = exceptionHandler.handleAuthenticationException(ex2, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response2.getStatusCode());
        assertEquals("Unauthorized", response2.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleAccessDenied -> 403 Forbidden")
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied message");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleGenericException -> 500 Internal Server Error")
    void testHandleGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected runtime error");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleStorageQuotaExceeded -> 403 Forbidden")
    void testHandleStorageQuotaExceeded() {
        StorageQuotaExceededException ex = new StorageQuotaExceededException("Xoá scan cũ để tiếp tục");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleStorageQuotaExceeded(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Xoá scan cũ để tiếp tục", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("Test handleHttpMessageNotReadable -> 400 Bad Request")
    void testHandleHttpMessageNotReadable() {
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                new org.springframework.http.converter.HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpMessageNotReadable(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Malformed JSON request or invalid field format", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Test handleIllegalArgument -> 400 Bad Request")
    void testHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid sort field");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Invalid sort field", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Test handleConstraintViolation with violations and empty violations -> 400 Bad Request")
    void testHandleConstraintViolation() {
        jakarta.validation.ConstraintViolation<?> violation = mock(jakarta.validation.ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("Page must be at least 1");

        jakarta.validation.ConstraintViolationException exWithViolations =
                new jakarta.validation.ConstraintViolationException("Validation error", java.util.Set.of(violation));
        ResponseEntity<ErrorResponse> response1 = exceptionHandler.handleConstraintViolation(exWithViolations, request);

        assertEquals(HttpStatus.BAD_REQUEST, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertEquals(400, response1.getBody().getStatus());
        assertEquals("Page must be at least 1", response1.getBody().getMessage());

        jakarta.validation.ConstraintViolationException exEmpty =
                new jakarta.validation.ConstraintViolationException("Validation error", java.util.Collections.emptySet());
        ResponseEntity<ErrorResponse> response2 = exceptionHandler.handleConstraintViolation(exEmpty, request);

        assertEquals(HttpStatus.BAD_REQUEST, response2.getStatusCode());
        assertNotNull(response2.getBody());
        assertEquals(400, response2.getBody().getStatus());
        assertEquals("Validation constraint violation", response2.getBody().getMessage());
    }

    @Test
    @DisplayName("Test handleScanConflict -> 409 Conflict")
    void testHandleScanConflict() {
        ScanConflictException ex = new ScanConflictException("Cannot update scan while it is in processing status");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleScanConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Cannot update scan while it is in processing status", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
        assertNotNull(response.getBody().getTimestamp());
    }

    private void dummyMethod(String param) {
    }
}
