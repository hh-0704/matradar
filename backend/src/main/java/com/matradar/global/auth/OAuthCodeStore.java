package com.matradar.global.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * OAuth 로그인 성공 → 프론트 복귀 사이에 <b>토큰을 URL 로 흘리지 않기 위한</b> 일회용 code 저장소.
 *
 * <p>로그인 성공 핸들러는 토큰 대신 짧은 수명의 opaque code 만 쿼리로 실어 프론트로 보내고,
 * 프론트가 {@code POST /api/auth/exchange} 로 code 를 토큰 쌍과 바꾼다. 토큰이 브라우저 히스토리·
 * 액세스 로그·Referer 헤더에 남지 않는다.
 *
 * <p>Redis 접근은 이런 전용 Store 클래스로만 한다 — 서비스가 {@code RedisTemplate} 을 직접 만지지 않아
 * 키 규칙·TTL 이 한 파일에 모인다.
 */
@Component
@RequiredArgsConstructor
public class OAuthCodeStore {

    private static final String KEY_PREFIX = "oauth_code:";
    // 로그인 직후 프론트가 곧바로 교환하므로 짧게 — 유출돼도 쓸 수 있는 창이 좁다.
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int CODE_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;

    /** 새 일회용 code 를 발급하고 userId 매핑을 저장한다. */
    public String issue(Long userId) {
        String code = generateCode();
        redisTemplate.opsForValue().set(key(code), String.valueOf(userId), TTL);
        return code;
    }

    /**
     * code 를 단 한 번만 소비한다 — {@code GETDEL} 로 조회와 삭제를 원자 처리해 재사용(replay)을 막는다.
     * 조회 후 삭제를 따로 보내면 그 사이에 같은 code 로 두 번 교환될 수 있다.
     */
    public Optional<Long> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String userId = redisTemplate.opsForValue().getAndDelete(key(code));
        return userId == null ? Optional.empty() : Optional.of(Long.valueOf(userId));
    }

    private static String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
