package com.matradar.domain.place.service;

// 발췌 — 가게 등록의 멱등성 처리 부분만 싣는다. 검색·조회·중복 가게 판정 등은 생략.

import com.matradar.domain.place.dto.CreatePlaceRequest;
import com.matradar.domain.place.entity.Place;
import com.matradar.domain.place.entity.PlaceCreationIdempotency;
import com.matradar.domain.place.exception.DuplicatePlaceException;
import com.matradar.domain.place.repository.PlaceCreationIdempotencyRepository;
import com.matradar.domain.user.service.UserService;
import com.matradar.global.exception.BusinessException;
import com.matradar.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int IDEMPOTENCY_TTL_HOURS = 24;

    private final PlaceCreationIdempotencyRepository idempotencyRepository;
    private final UserService userService;
    // … 그 밖의 의존성 생략

    /**
     * 새 가게 등록.
     *
     * <p>순서: 정지 계정 차단 → 멱등키·바디 검증 → 멱등 기록 조회(재응답 / 키 오용 판정) → 중복 가게 판정 → 저장 → 멱등 결과 기록.
     *
     * @throws BusinessException       정지 계정({@code USER_002}) / 검증 실패({@code PLACE_006}) / 멱등키 오용({@code PLACE_007})
     * @throws DuplicatePlaceException 이미 등록된 가게({@code PLACE_003}, 기존 placeId 동반)
     */
    @Transactional
    public Place createPlace(CreatePlaceRequest request, String idempotencyKey, Long userId) {
        // 토큰이 아니라 DB 의 정지 여부를 본다 — 정지가 즉시 반영된다.
        if (userService.isSuspended(userId)) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw invalidRequest("X-Idempotency-Key 헤더가 필요합니다");
        }
        validateRequest(request);

        String bodyHash = computeBodyHash(request);

        Optional<PlaceCreationIdempotency> stored = idempotencyRepository.findById(idempotencyKey);
        if (stored.isPresent()) {
            PlaceCreationIdempotency record = stored.get();
            if (isExpired(record)) {
                // 만료 키 — 새 키처럼 취급한다
                idempotencyRepository.delete(record);
            } else if (record.getBodyHash().equals(bodyHash)) {
                // 같은 키 + 같은 바디 — 다시 처리하지 않고 저장된 결과를 재응답
                return findById(record.getPlaceId());
            } else {
                // 같은 키 + 다른 바디 — 키 오용
                throw new BusinessException(ErrorCode.PLACE_IDEMPOTENCY_CONFLICT);
            }
        }

        Place duplicate = findDuplicate(request);
        if (duplicate != null) {
            throw new DuplicatePlaceException(duplicate.getId());
        }

        Place saved = savePlace(request);
        // 같은 멱등키로 동시 요청이 오면 여기서 PK 충돌이 난다. 즉시 flush 해 그 자리에서 제약 위반으로 받고,
        // 복구는 트랜잭션 밖(컨트롤러)이 한 번 재시도해서 한다 — 재시도 때는 먼저 성공한 쪽이 커밋돼 있어
        // 위의 "저장된 결과 재응답" 경로를 탄다. 제약 위반이 난 트랜잭션은 이미 rollback-only 라
        // 같은 트랜잭션 안에서 할 수 있는 복구가 없다.
        idempotencyRepository.saveAndFlush(
                PlaceCreationIdempotency.create(idempotencyKey, bodyHash, saved.getId()));
        return saved;
    }

    private boolean isExpired(PlaceCreationIdempotency record) {
        return record.getCreatedAt()
                .isBefore(LocalDateTime.now().minusHours(IDEMPOTENCY_TTL_HOURS));
    }

    /** 키 오용 감지용 요청 해시 — 의미가 같은 요청은 같은 해시가 되도록 정규화한 필드만 이어 붙인다. */
    private static String computeBodyHash(CreatePlaceRequest request) {
        String canonical = String.join("|",
                normalizeName(request.name()),
                nullToEmpty(request.address()),
                nullToEmpty(request.roadAddress()),
                String.valueOf(request.lat()),
                String.valueOf(request.lng()),
                nullToEmpty(request.category()),
                nullToEmpty(request.source()));
        return sha256Hex(canonical);
    }

    /** trim · 내부 공백 압축 · 소문자. */
    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
    }

    // … validateRequest · findDuplicate · savePlace · findById · invalidRequest 생략
}
