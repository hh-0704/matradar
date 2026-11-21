package com.matradar.global.exception;

import com.matradar.global.auth.AccessDeniedErrorCodeResolver;
import com.matradar.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 {@link ApiResponse} 봉투로 수렴시킨다. (발췌)
 *
 * <ul>
 *   <li>{@link ResponseEntityExceptionHandler} 를 상속해 표준 Web 예외(파라미터 누락·타입 불일치·없는 경로 등)가
 *       500 이 아니라 원래 상태 코드 그대로 봉투에 담기게 한다.</li>
 *   <li>응답에 추가 정보가 필요한 예외(기존 가게 id, 재시도 가능 시각, Retry-After)는 별도 핸들러로 뺀다.
 *       {@code @ExceptionHandler} 는 가장 구체적인 타입을 고르므로 상위 핸들러와 충돌하지 않는다.</li>
 *   <li>마지막 {@code Exception} 핸들러는 진짜 서버 버그의 보호막이다 — 스택은 로그에만, 응답은 일반 문구만.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * 보호 장치의 일시 거절(예: 사진 처리 메모리 예산). 503 에 {@code Retry-After} 를 붙여야 해
     * 별도 핸들러다 — nginx 의 429 와 같은 헤더라 클라이언트는 한 가지 방식으로 기다린다.
     */
    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceBusy(ServiceBusyException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("ServiceBusyException: code={}, retryAfterSeconds={}", errorCode.getCode(), e.getRetryAfterSeconds());
        return ResponseEntity
                .status(errorCode.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * 컨트롤러 {@code @PreAuthorize} 인가 실패.
     *
     * <p>URL 매처 단계의 거부는 필터의 AccessDeniedHandler 가 받지만, method security 는 DispatcherServlet 안에서
     * 던져져 이 advice 로 온다. 핸들러가 없으면 아래 catch-all 이 잡아 <b>403 이어야 할 응답이 500</b> 이 된다.
     * 두 경로가 같은 403 봉투로 수렴해야 "URL 매처 + 메서드 보안" 이중 방어가 실제로 이중이 된다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        ErrorCode errorCode = AccessDeniedErrorCodeResolver.resolve(); // 게스트면 AUTH_010, 그 외 COMMON_004
        log.warn("AccessDenied: code={} message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    // … 기존 가게 id · 재시도 가능 시각을 data 에 싣는 핸들러, 검증 실패(필드별 메시지) 핸들러 생략

    /** {@link ResponseEntityExceptionHandler} 가 처리하는 표준 예외는 전부 여기로 모인다 — 기본 ProblemDetail 대신 봉투로. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Standard web exception: type={}, status={}, message={}",
                ex.getClass().getSimpleName(), statusCode, ex.getMessage());
        ApiResponse<Void> apiBody = ApiResponse.error(
                ErrorCode.COMMON_INVALID_INPUT.getCode(),
                ErrorCode.COMMON_INVALID_INPUT.getMessage());
        return new ResponseEntity<>(apiBody, headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.COMMON_INTERNAL_ERROR;
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
