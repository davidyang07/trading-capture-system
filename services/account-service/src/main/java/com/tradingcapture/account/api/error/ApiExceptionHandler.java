package com.tradingcapture.account.api.error;

import com.tradingcapture.account.domain.exception.AccountDomainException;
import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.InvalidRequestException;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.security.ProblemDetails;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Maps every failure to the standardized problem contract (shared {@link ProblemDetails}).
 * Domain rules decide the status: not-found (incl. masked ownership denials) 404, state
 * conflicts 409, semantic input errors 400. Unexpected failures return an opaque 500 that
 * keeps the correlation ID but leaks no internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccountDomainException.class)
    public ProblemDetail handleDomain(AccountDomainException ex) {
        HttpStatus status = statusOf(ex);
        log.info("Domain rejection ({}): {}", ex.errorCode(), ex.getMessage());
        return ProblemDetails.build(status, ex.errorCode().name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        return validationProblem(ex.getBindingResult().getFieldErrors());
    }

    /** Query-parameter binding failures for @ModelAttribute records (search endpoints). */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBinding(BindException ex) {
        return validationProblem(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerValidation(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetails.build(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed");
        problem.setProperty("fieldErrors", ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName() == null
                                        ? "parameter" : result.getMethodParameter().getParameterName(),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList());
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetails.build(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed");
        problem.setProperty("fieldErrors", ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", String.valueOf(violation.getPropertyPath()),
                        "message", violation.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetails.build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST.name(),
                "Request body is malformed or contains an invalid value");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetails.build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST.name(),
                "Parameter '" + ex.getName() + "' has an invalid value");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.info("Optimistic lock conflict: {}", ex.getMessage());
        return ProblemDetails.build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION.name(),
                "The account was modified concurrently; retry the request");
    }

    /** A row lock could not be taken in time (lock-wait timeout or deadlock victim): safe to retry. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handlePessimisticLock(PessimisticLockingFailureException ex) {
        log.info("Lock conflict: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetails.build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION.name(),
                "The account is busy with another update; retry the request");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        // Unique/check/FK constraints are the last line of defense against write races
        // (e.g. two concurrent posts with the same idempotency key).
        log.info("Data integrity conflict: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetails.build(HttpStatus.CONFLICT, ErrorCode.CONSTRAINT_VIOLATION.name(),
                "The request conflicts with the current state of the data");
    }

    /**
     * Security exceptions must reach Spring Security's ExceptionTranslationFilter (which
     * produces the standardized 401/403 problem responses) — a catch-all handler would
     * otherwise swallow them into a 500. Rethrowing is the documented delegation pattern.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void delegateSecurityExceptions(RuntimeException ex) {
        throw ex;
    }

    /** Unknown URLs stay 404 in the standard problem shape (the catch-all must not see them). */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleUnknownPath(Exception ex) {
        return ProblemDetails.build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.name(),
                "The requested resource does not exist");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled failure processing request", ex);
        return ProblemDetails.build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred");
    }

    private ProblemDetail validationProblem(List<FieldError> fieldErrors) {
        ProblemDetail problem = ProblemDetails.build(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed");
        problem.setProperty("fieldErrors", fieldErrors.stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList());
        return problem;
    }

    private HttpStatus statusOf(AccountDomainException ex) {
        if (ex instanceof ResourceNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof BusinessRuleException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof InvalidRequestException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
