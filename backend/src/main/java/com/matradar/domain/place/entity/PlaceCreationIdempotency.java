package com.matradar.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 가게 등록({@code POST /api/places}) 멱등성 기록.
 *
 * <p>{@code X-Idempotency-Key} 헤더값을 PK 로 삼아 (키, 요청 해시, 결과 placeId) 를 보관한다.
 * 같은 키로 다시 오면 저장된 결과를 재응답하고, 같은 키에 다른 바디면 키 오용으로 거부한다.
 * 만료는 {@code createdAt} 기준으로 등록 서비스가 판단한다({@code PlaceService#createPlace}).
 */
@Entity
@Table(name = "place_creation_idempotency")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCreationIdempotency {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "body_hash", nullable = false, length = 64)
    private String bodyHash;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PlaceCreationIdempotency(String idempotencyKey, String bodyHash, Long placeId) {
        this.idempotencyKey = idempotencyKey;
        this.bodyHash = bodyHash;
        this.placeId = placeId;
    }

    public static PlaceCreationIdempotency create(String idempotencyKey, String bodyHash, Long placeId) {
        return new PlaceCreationIdempotency(idempotencyKey, bodyHash, placeId);
    }
}
