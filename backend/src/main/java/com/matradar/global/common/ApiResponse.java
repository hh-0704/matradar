package com.matradar.global.common;

/**
 * 모든 API 응답의 봉투. 성공이면 {@code data}, 실패면 {@code errorCode}·{@code message} 가 채워진다.
 *
 * <p>프론트는 이 봉투를 axios 인터셉터 한 곳에서 벗기고, 실패는 {@code errorCode} 로만 분기한다
 * ({@code frontend/src/api/client.ts}). 화면 문구는 프론트의 errorCode → 메시지 매핑이 단일 출처다.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
