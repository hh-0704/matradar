// 가게 DTO → 도메인 모델 변환 — DTO / mapper / 도메인 3층의 가운데.
// 백엔드 필드명(nameSnapshot 등)을 화면 친화 이름으로 바꾸고, 누락·이상치를 여기서 **혼자** 흡수한다.
// 화면 컴포넌트는 null 체크 없이 도메인 모델을 쓴다 — 백엔드 필드가 바뀌면 고칠 곳이 이 파일 하나다.
// (발췌 — 카드용 매퍼 등 생략)

import type { PlaceResponseDto } from '@/types/dto/place';
import type {
  MichelinRating,
  Place,
  PlaceAccolades,
  PlaceDetail,
  RatingDistribution,
} from '@/types/place';

export function mapPlace(dto: PlaceResponseDto): Place {
  return {
    id: dto.id,
    name: dto.nameSnapshot,
    lat: dto.lat,
    lng: dto.lng,
  };
}

export function mapPlaceDetail(dto: PlaceResponseDto): PlaceDetail {
  return {
    ...mapPlace(dto),
    kakaoPlaceId: dto.kakaoPlaceId,
    accolades: mapAccolades(dto),
    // 필드가 없는 과거 응답이면 false — 근거 없이 경고 배너를 띄우지 않는다.
    advertisingWarning: dto.advertisingWarning ?? false,
    reviewCount: dto.ratingCount,
    ratingAverage: dto.ratingAverage,
    ratingDistribution: mapRatingDistribution(dto.ratingDistribution),
  };
}

const RATING_BUCKETS = [1, 2, 3, 4, 5] as const;

// 별점 분포. 키가 통째로 없으면 null — 화면이 분포를 숨긴다. 0 으로 채우면 리뷰가 있는 가게에 빈 그래프가 떠
// "아무도 평가 안 함" 으로 읽힌다. 객체는 있는데 일부 칸이 빠졌거나 숫자가 아니면 그 칸만 0.
function mapRatingDistribution(
  dto: PlaceResponseDto['ratingDistribution'],
): RatingDistribution | null {
  if (!dto) {
    if (__DEV__) {
      console.warn('[place] 가게 상세 응답에 ratingDistribution 이 없다 — 별점 분포를 숨긴다');
    }
    return null;
  }
  const distribution = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } as RatingDistribution;
  for (const bucket of RATING_BUCKETS) {
    const value = dto[String(bucket) as keyof typeof dto];
    distribution[bucket] = typeof value === 'number' && value >= 0 ? value : 0;
  }
  return distribution;
}

// 수상·방송 이력 — 항상 채워진 객체를 돌려줘 컴포넌트가 null 체크 없이 쓴다.
function mapAccolades(dto: PlaceResponseDto): PlaceAccolades {
  return {
    michelin: normalizeMichelin(dto.michelin),
    blueRibbon: dto.blueRibbon ?? false,
    // program 이 비었거나 문자열이 아닌 항목은 거른다 — null 에 trim() 을 부르면 상세 매핑 전체가 깨진다.
    tvAppearances: (dto.tvAppearances ?? []).filter(
      (tv) => typeof tv.program === 'string' && tv.program.trim().length > 0,
    ),
  };
}

// 미슐랭 스타는 1~3 만 유효 — 범위 밖은 미선정(null).
function normalizeMichelin(
  michelin: PlaceResponseDto['michelin'],
): MichelinRating | null {
  if (!michelin) return null;
  const { stars, year } = michelin;
  if (stars !== 1 && stars !== 2 && stars !== 3) return null;
  return { stars, ...(typeof year === 'number' ? { year } : {}) };
}
