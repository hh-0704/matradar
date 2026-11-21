package com.matradar.domain.photo.service;

import com.matradar.domain.photo.config.ImageProcessingProperties;
import com.matradar.global.exception.ErrorCode;
import com.matradar.global.exception.ServiceBusyException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사진 처리의 전역 메모리 예산 — <b>건수가 아니라 예상 메모리로</b> 동시 처리를 묶는다.
 *
 * <p>건수 제한(예: 동시 2장)으로는 크기 차이를 다루지 못한다. 프론트가 보내는 2048px JPEG 는 처리 중 약 34MB 지만
 * 업로드 상한 크기의 16비트 PNG 는 약 270MB 다. 건수를 큰 쪽에 맞추면 리뷰 한 건의 사진 5장(병렬 업로드)도
 * 줄을 서고, 작은 쪽에 맞추면 큰 사진 몇 장으로 힙이 넘친다. 그래서 1MiB 를 한 단위로 하는 공정(FIFO)
 * 세마포어에서 사진마다 예상 메모리만큼 가져간다({@link ImageMemoryEstimator}).
 *
 * <p>순서: 헤더로 크기를 본 뒤 <b>디코드 전에</b> 확보하고, 인코딩·저장이 끝나거나 예외가 나면 반드시 반환한다
 * ({@link Permit} 을 try-with-resources 로 쓴다). 예산이 모자라면 정해진 시간까지 기다리되, 대기 인원이 이미 차
 * 있으면 바로 503 {@code PHOTO_001} 이다 — 대기열을 무제한으로 쌓으면 요청 스레드가 묶여 다른 API 까지 멈춘다.
 */
@Slf4j
@Component
public class ImageProcessingLimiter {

    private static final long MIB = 1024L * 1024L;
    private static final Duration RETRY_AFTER = Duration.ofSeconds(3);

    private final Semaphore budget;
    private final int totalPermits;
    private final Duration acquireTimeout;
    private final int maxWaiting;
    private final AtomicInteger waiting = new AtomicInteger();

    public ImageProcessingLimiter(ImageProcessingProperties properties) {
        long worstCase = properties.maxPixels() * ImageMemoryEstimator.WORST_CASE_BYTES_PER_PIXEL;
        if (worstCase > properties.memoryBudget().toBytes()) {
            // 상한 크기 한 장이 예산을 넘으면 그 사진은 영원히 확보하지 못한다 — 설정 실수를 부팅에서 끊는다.
            throw new IllegalArgumentException(String.format(
                    "사진 처리 메모리 예산(%dMB)이 상한 크기 한 장의 최악 추정(%dMB)보다 작습니다",
                    properties.memoryBudget().toMegabytes(), worstCase / MIB));
        }
        this.totalPermits = Math.toIntExact(properties.memoryBudget().toBytes() / MIB);
        this.budget = new Semaphore(totalPermits, true);
        this.acquireTimeout = properties.acquireTimeout();
        this.maxWaiting = properties.maxWaiting();
    }

    /**
     * 예상 메모리만큼 예산을 확보한다.
     *
     * @throws ServiceBusyException 503 {@code PHOTO_001} — 대기 인원 초과 또는 대기 시간 초과
     */
    public Permit acquire(long estimatedBytes) {
        int permits = toPermits(estimatedBytes);
        try {
            // 대기 시간 0 의 tryAcquire 는 공정성을 지킨다. 인자 없는 tryAcquire 는 줄을 새치기해서,
            // 작은 요청이 계속 새치기하면 큰 요청이 굶는다.
            if (budget.tryAcquire(permits, 0, TimeUnit.NANOSECONDS)) {
                return new Permit(permits);
            }
            if (waiting.incrementAndGet() > maxWaiting) {
                waiting.decrementAndGet();
                throw busy("대기 인원 초과", permits);
            }
            try {
                if (budget.tryAcquire(permits, acquireTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    return new Permit(permits);
                }
            } finally {
                waiting.decrementAndGet();
            }
            throw busy("대기 시간 초과", permits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy("대기 중 인터럽트", permits);
        }
    }

    private int toPermits(long estimatedBytes) {
        long permits = (Math.max(0L, estimatedBytes) + MIB - 1) / MIB;
        // 생성자에서 상한 크기 한 장이 예산 안에 든다는 걸 확인했지만, 넘더라도 영원히 기다리지 않게 전체 예산으로 자른다.
        return (int) Math.max(1L, Math.min(totalPermits, permits));
    }

    private ServiceBusyException busy(String reason, int permits) {
        log.warn("사진 처리 예산 부족 — {}: 요청 {}MB, 남은 예산 {}MB/{}MB, 대기 {}",
                reason, permits, budget.availablePermits(), totalPermits, waiting.get());
        return new ServiceBusyException(ErrorCode.PHOTO_PROCESSING_BUSY, RETRY_AFTER);
    }

    /** 확보한 예산. {@link #close()} 는 한 번만 반환한다 — 두 번 닫아도 예산이 부풀지 않는다. */
    public final class Permit implements AutoCloseable {

        private final int permits;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(int permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                budget.release(permits);
            }
        }
    }
}
