// 디자인 토큰 (발췌 — 별점 토큰만). 컴포넌트는 raw 헥스값을 쓰지 않고 토큰만 참조한다.
//
// 별점의 색 · 그라데이션 · 글로우 · 반짝임 · 라벨 · 설명을 점수별로 한 곳에 모은다.
// 별점 입력(StarRating)과 표시 컴포넌트가 같은 규칙을 공유한다.
const rating = {
  1: {
    label: '실망',
    description: '맛없음, 재방문 의사 전혀 없음',
    star: '#9CA3AF',
    gradient: null,
    gradientAngleDeg: 0,
    glow: null,
    sparkle: null,
  },
  2: {
    label: '보통',
    description: '맛은 보통, 재방문 의사 없음',
    star: '#FDE68A',
    gradient: null,
    gradientAngleDeg: 0,
    glow: null,
    sparkle: null,
  },
  3: {
    label: '괜찮음',
    description: '맛있게 먹음, 재방문 의사 있음',
    star: '#FBBF24',
    gradient: null,
    gradientAngleDeg: 0,
    glow: null,
    sparkle: null,
  },
  4: {
    label: '맛집',
    description: '맛집, 다시 오고 싶은 집',
    star: '#F59E0B',
    gradient: ['#FCD34D', '#F59E0B', '#D97706'],
    gradientAngleDeg: 120,
    glow: { color: '#F59E0B', radius: 16, opacity: 0.55 },
    sparkle: null,
  },
  5: {
    label: '단골',
    description: '완전 맛집, 단골집으로 찜',
    star: '#F59E0B',
    gradient: ['#FF3D71', '#FF8B00', '#FFD400', '#36B37E', '#0065FF', '#6554C0'],
    gradientAngleDeg: 120,
    glow: { color: '#FFD400', radius: 20, opacity: 0.7 },
    sparkle: { scale: 1.12, durationMs: 1800 },
  },
} as const;

export type Rating = keyof typeof rating;

// … colors · spacing · typography · motion 등 생략
