package uz.usta.platform.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import uz.usta.platform.shared.web.RequestCorrelationFilter;

/**
 * Yagona xatolik formati — RFC 9457 {@code application/problem+json}:
 *
 * <pre>{@code
 * {
 *   "type": "urn:usta:error:validation_failed",
 *   "title": "So'rov ma'lumotlari noto'g'ri",
 *   "status": 400,
 *   "detail": "So'rov ma'lumotlari noto'g'ri",
 *   "instance": "/api/v1/...",
 *   "code": "VALIDATION_FAILED",
 *   "traceId": "6f1c...",
 *   "timestamp": "2026-11-17T10:00:00Z",
 *   "errors": [ { "field": "phone", "message": "..." } ]
 * }
 * }</pre>
 *
 * Spring MVC'ning standart exception'lari {@link ResponseEntityExceptionHandler} orqali, domen va
 * xavfsizlik exception'lari quyidagi {@code @ExceptionHandler}'lar orqali shu formatga keltiriladi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:usta:error:";
    /** Enum nomi yoki ularning ro'yxati: {@code IN_PROGRESS}, {@code PASSPORT, SELFIE}. */
    private static final Pattern CODE_LIST = Pattern.compile("[A-Z][A-Z_]+(, [A-Z][A-Z_]+)*");

    private final MessageSource messages;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    // ---------------------------------------------------------------- domen

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("Business error {}", code.code(), ex);
        } else {
            log.debug("Business error {}: {}", code.code(), ex.getMessage());
        }
        ProblemDetail pd = problem(code, request.getRequestURI(), ex.getArgs());
        ex.getDetails().forEach(pd::setProperty);
        if (ex.getDetails().get("retryAfterSeconds") instanceof Number retryAfter) {
            return ResponseEntity.status(HttpStatus.valueOf(pd.getStatus()))
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.longValue()))
                    .body(pd);
        }
        return respond(pd);
    }

    // ---------------------------------------------------------------- xavfsizlik

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return respond(problem(CommonErrorCode.UNAUTHORIZED, request.getRequestURI()));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex, HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        ErrorCode code = (auth == null || trustResolver.isAnonymous(auth))
                ? CommonErrorCode.UNAUTHORIZED
                : CommonErrorCode.ACCESS_DENIED;
        return respond(problem(code, request.getRequestURI()));
    }

    // ---------------------------------------------------------------- ma'lumotlar bazasi

    /** {@code ?sort=} da mavjud bo'lmagan maydon — mijoz xatosi. */
    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ProblemDetail> handleUnknownProperty(PropertyReferenceException ex, HttpServletRequest request) {
        log.debug("Unknown property on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(problem(CommonErrorCode.BAD_REQUEST, request.getRequestURI()));
    }

    /** So'rov (query) darajasidagi xato: {@code ?sort=} berilgan bo'lsa — o'sha sabab (400), aks holda bizniki. */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ResponseEntity<ProblemDetail> handleQueryUsage(InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
        if (request.getParameter("sort") == null) {
            return handleUnexpected(ex, request);
        }
        log.debug("Invalid sort on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(problem(CommonErrorCode.BAD_REQUEST, request.getRequestURI()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleLocking(RuntimeException ex, HttpServletRequest request) {
        log.info("Concurrent modification on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(problem(CommonErrorCode.CONCURRENT_MODIFICATION, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(problem(CommonErrorCode.CONFLICT, request.getRequestURI()));
    }

    // ---------------------------------------------------------------- validatsiya (servis qatlami)

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(CommonErrorCode.VALIDATION_FAILED, request.getRequestURI());
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return respond(pd);
    }

    // ---------------------------------------------------------------- fallback

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(problem(CommonErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }

    // ---------------------------------------------------------------- Spring MVC exception'lari

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        List<FieldViolation> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldViolation(fe.getField(), messages.getMessage(fe, locale)));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new FieldViolation(oe.getObjectName(), messages.getMessage(oe, locale)));
        }
        ProblemDetail pd = problem(CommonErrorCode.VALIDATION_FAILED, path(request));
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err -> errors.add(new FieldViolation(
                    err instanceof FieldError fe ? fe.getField() : param,
                    messages.getMessage(err, locale))));
        });
        ProblemDetail pd = problem(CommonErrorCode.VALIDATION_FAILED, path(request));
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /**
     * Qolgan barcha standart MVC exception'lari shu yerdan o'tadi — ularni ham yagona formatga
     * keltiramiz (kod, lokalizatsiya qilingan matn, traceId).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getProperties() != null && pd.getProperties().containsKey("code")) {
            return super.handleExceptionInternal(ex, pd, headers, statusCode, request);
        }
        ErrorCode code = mapMvcException(ex, statusCode);
        ProblemDetail pd = problem(code, path(request));
        pd.setStatus(statusCode.value());
        if (statusCode.is5xxServerError()) {
            log.error("MVC error on {}", path(request), ex);
        }
        return super.handleExceptionInternal(ex, pd, headers, statusCode, request);
    }

    private static ErrorCode mapMvcException(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case MethodArgumentNotValidException e -> CommonErrorCode.VALIDATION_FAILED;
            case HandlerMethodValidationException e -> CommonErrorCode.VALIDATION_FAILED;
            case HttpMessageNotReadableException e -> CommonErrorCode.MALFORMED_REQUEST;
            case MissingServletRequestParameterException e -> CommonErrorCode.MALFORMED_REQUEST;
            case ServletRequestBindingException e -> CommonErrorCode.MALFORMED_REQUEST;
            case TypeMismatchException e -> CommonErrorCode.MALFORMED_REQUEST;
            case NoResourceFoundException e -> CommonErrorCode.NOT_FOUND;
            case NoHandlerFoundException e -> CommonErrorCode.NOT_FOUND;
            case HttpRequestMethodNotSupportedException e -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException e -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case HttpMediaTypeNotAcceptableException e -> CommonErrorCode.NOT_ACCEPTABLE;
            case MaxUploadSizeExceededException e -> CommonErrorCode.PAYLOAD_TOO_LARGE;
            default -> status.is5xxServerError() ? CommonErrorCode.INTERNAL_ERROR : CommonErrorCode.BAD_REQUEST;
        };
    }

    // ---------------------------------------------------------------- yordamchi

    private ProblemDetail problem(ErrorCode code, String instance, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        String template = messages.getMessage(code.messageKey(), null, code.code(), locale);
        String detail = args.length == 0 ? template
                : messages.getMessage(code.messageKey(), localized(code, args, locale), template, locale);
        // sarlavhada to'ldirilmagan {0} ko'rinmasin
        String title = template.contains("{0}") ? detail : template;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setType(URI.create(TYPE_PREFIX + code.code().toLowerCase(Locale.ROOT)));
        pd.setTitle(title);
        if (instance != null) {
            pd.setInstance(URI.create(instance));
        }
        pd.setProperty("code", code.code());
        pd.setProperty("traceId", MDC.get(RequestCorrelationFilter.MDC_KEY));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    /**
     * Xabarga qo'yiladigan kodlarni o'qiladigan matnga o'giradi: resurs nomi ({@code resource.*}) hamda holat va tur
     * nomlari ({@code status.*}). Tarjimasi yo'q qiymat o'zicha qoladi.
     */
    private Object[] localized(ErrorCode code, Object[] args, Locale locale) {
        Object[] result = args.clone();
        for (int i = 0; i < result.length; i++) {
            if (!(result[i] instanceof String value)) {
                continue;
            }
            if (code == CommonErrorCode.RESOURCE_NOT_FOUND && i == 0) {
                result[i] = messages.getMessage("resource." + value, null, value, locale);
            } else if (CODE_LIST.matcher(value).matches()) {
                result[i] = Arrays.stream(value.split(", "))
                        .map(c -> messages.getMessage("status." + c, null, c, locale))
                        .collect(Collectors.joining(", "));
            }
        }
        return result;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail pd) {
        return ResponseEntity.status(HttpStatus.valueOf(pd.getStatus())).body(pd);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }

    private static String lastNode(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx >= 0 ? propertyPath.substring(idx + 1) : propertyPath;
    }
}
