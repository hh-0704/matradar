package com.matradar.domain.photo.service;

import java.awt.image.DataBuffer;
import java.io.IOException;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;

/**
 * 사진 한 장의 처리(디코드 → 흰 배경 합성 → JPEG 인코딩) 피크 메모리 추정 — 헤더만 보고 디코드 전에 계산한다.
 *
 * <p>픽셀당 바이트는 실측값을 올림한 것이다. 힙을 줄여 가며 처리가 성공하는 최소 힙을 찾는 방식으로 쟀다.
 * <ul>
 *   <li>JPEG — 실측 약 6.8 B/px → <b>8</b> (디코드 BGR + 디코더 임시 버퍼 + 합성 버퍼)</li>
 *   <li>8비트 PNG — 실측 약 11.6 B/px → <b>12</b> (알파가 있으면 합성 중 중간 버퍼가 더 생긴다)</li>
 *   <li>16비트 PNG·판별 불가 — 실측 약 15.4 B/px → <b>16</b></li>
 * </ul>
 * 과소 추정은 OOM 이고 과대 추정은 대기라, 애매하면 큰 쪽으로 둔다.
 */
final class ImageMemoryEstimator {

    static final int JPEG_BYTES_PER_PIXEL = 8;
    static final int PNG_8BIT_BYTES_PER_PIXEL = 12;
    static final int WORST_CASE_BYTES_PER_PIXEL = 16;

    private ImageMemoryEstimator() {
    }

    static long estimateBytes(ImageReader reader, long pixels) throws IOException {
        return pixels * bytesPerPixel(reader);
    }

    static int bytesPerPixel(ImageReader reader) throws IOException {
        String format = reader.getFormatName();
        if ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format)) {
            return JPEG_BYTES_PER_PIXEL;
        }
        if ("png".equalsIgnoreCase(format)) {
            ImageTypeSpecifier raw = reader.getRawImageType(0);
            if (raw != null && raw.getSampleModel().getDataType() == DataBuffer.TYPE_BYTE) {
                return PNG_8BIT_BYTES_PER_PIXEL;
            }
        }
        return WORST_CASE_BYTES_PER_PIXEL;
    }
}
