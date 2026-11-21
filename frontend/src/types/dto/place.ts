// 발췌 — DTO 층은 백엔드 응답과 1:1 이다. 화면 편의를 위한 필드를 여기에 더하지 않는다(그건 mapper 의 몫).

export type PlaceResponseDto = {
  id: number;
  kakaoPlaceId: string | null;
  nameSnapshot: string;
  lat: number;
  lng: number;
  ratingAverage: number;
  ratingCount: number;
  michelin?: { stars: number; year?: number } | null;
  blueRibbon?: boolean | null;
  tvAppearances?: { program: string; episode?: string; airedAt?: string }[] | null;
  advertisingWarning?: boolean;
  ratingDistribution?: Partial<Record<'1' | '2' | '3' | '4' | '5', number>> | null;
};

// … 카드·검색·등록 요청 DTO 생략
