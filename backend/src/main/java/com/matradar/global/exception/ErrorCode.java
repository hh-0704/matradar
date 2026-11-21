package com.matradar.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 단일 출처 — HTTP 상태 · 코드 · 기본 메시지를 한 자리에 둔다. (발췌 — 일부 코드만 싣는다)
 *
 * <p>코드를 나누는 기준은 <b>"프론트가 사용자에게 다른 안내를 해야 하는가"</b> 다. 같은 403·503 이라도
 * 사용자가 할 일이 다르면 코드를 가른다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // COMMON
    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청입니다."),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_003", "인증이 필요합니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "서버 내부 오류가 발생했습니다."),
    // nginx 의 요청 제한 응답과 같은 코드를 쓴다 — 어느 층에서 막혔든 프론트의 안내는 같다.
    COMMON_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "COMMON_006", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // AUTH
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "유효하지 않은 토큰입니다."),
    AUTH_REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_003", "리프레시 토큰을 찾을 수 없습니다."),
    // 관리자 로그인 실패는 사유(아이디 없음·비밀번호 불일치·권한 없음·정지)를 나누지 않는다 — 계정 존재 여부를 흘리지 않는다.
    AUTH_ADMIN_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "AUTH_006", "아이디 또는 비밀번호가 올바르지 않습니다."),
    // 일회용 code 교환 실패(만료·재사용·위조). 401 이 아니라 400 — 프론트의 401 → refresh 재시도 경로를 타면 안 된다.
    AUTH_INVALID_CODE(HttpStatus.BAD_REQUEST, "AUTH_007", "유효하지 않거나 만료된 인증 코드입니다."),
    // 관리자 동시 세션 1개 — 다른 기기의 로그인으로 밀려난 세션. 401 이지만 refresh 로 회복할 수 없어서
    // 프론트는 이 코드를 refresh 분기보다 먼저 가로채 곧바로 재로그인으로 보낸다.
    AUTH_SESSION_EVICTED(HttpStatus.UNAUTHORIZED, "AUTH_009", "다른 기기에서 로그인되어 현재 세션이 만료되었습니다."),
    // 게스트(읽기 전용) 계정의 쓰기. COMMON_004 와 나눈 이유 — 이쪽은 "로그인하면 할 수 있어요" 로 전환을 유도하고,
    // COMMON_004 는 막다른 길이다.
    AUTH_GUEST_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_010", "게스트는 이용할 수 없는 기능입니다. 로그인 후 이용해주세요."),

    // USER
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_002", "정지된 사용자입니다."),

    // PLACE
    PLACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PLACE_003", "이미 등록된 가게입니다."),
    PLACE_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "PLACE_006", "가게 정보를 확인해주세요."),
    PLACE_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "PLACE_007", "같은 요청 키로 다른 요청이 들어왔습니다."),

    // PHOTO
    // 사진 처리 메모리 예산이 잠시 모자람. 파일이 잘못된 것(COMMON_001)과 나눈 이유 — 이쪽은 같은 사진으로
    // 잠시 뒤 다시 하면 된다(Retry-After 동반).
    PHOTO_PROCESSING_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_001", "사진 처리 요청이 많아 잠시 업로드할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // REVIEW
    REVIEW_INVALID_RATING(HttpStatus.BAD_REQUEST, "REVIEW_002", "별점은 1~5점 사이여야 합니다."),
    REVIEW_INVALID_CONTENT_LENGTH(HttpStatus.BAD_REQUEST, "REVIEW_003", "리뷰 내용은 10자 이상 300자 이하여야 합니다."),
    // 응답 data 에 retryAvailableAt 이 실린다 — 프론트는 그 시각을 재계산하지 않고 그대로 보여준다.
    REVIEW_COOLDOWN(HttpStatus.CONFLICT, "REVIEW_005", "같은 가게에는 24시간에 한 번만 리뷰를 남길 수 있습니다."),

    // ADMIN
    // 본인 계정 정지 차단. 403 이 아니라 400 — 403 은 "관리자 권한 없음" 과 섞여 프론트가 재로그인으로 잘못 분기한다.
    ADMIN_CANNOT_SUSPEND_SELF(HttpStatus.BAD_REQUEST, "ADMIN_003", "본인 계정은 정지할 수 없습니다.");

    // … 그 밖의 코드는 생략

    private final HttpStatus status;
    private final String code;
    private final String message;
}
