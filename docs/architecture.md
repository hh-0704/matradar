# 구조

## 시스템 구성

```
                         ┌──────────────────────── Cloudflare ────────────────────────┐
  브라우저 ──HTTPS──▶    │  DNS · TLS · 프록시                                        │
                         │   ├─ 웹앱 ──────▶ Cloudflare Pages (Expo Web 정적 빌드)     │
                         │   └─ API  ──────▶ VPS (Origin 인증서, Full strict)          │
                         └────────────────────────────────┬───────────────────────────┘
                                                          ▼
                              ┌──────────────── VPS · Docker Compose ───────────────┐
                              │  nginx ──▶ Spring Boot ──▶ MySQL                     │
                              │  (요청 제한)      │      └──▶ Redis                  │
                              └───────────────────┼──────────────────────────────────┘
                                                  ├──▶ Google OAuth (로그인)
                                                  └──▶ 카카오 로컬 API (가게 검색)
```

- 웹앱은 정적 빌드라 서버가 없습니다. 지도는 브라우저에서 카카오맵 SDK 로 그립니다.
- nginx 는 Cloudflare 대역에서 온 요청에 한해서만 원래 클라이언트 IP 를 복원합니다. 다른 곳에서 보낸 헤더는 믿지 않습니다.
- DB 스키마는 Flyway 마이그레이션으로 관리하고, 애플리케이션이 뜰 때 적용됩니다.

## 백엔드

```
com.matradar
├── domain/<기능>/   controller · service · repository · entity · dto
│                    가게 · 리뷰 · 사진 · 제보 · 유저 · 배지 · 관리자 · (리뷰 신뢰 — 비공개)
├── global/          auth · common(응답 봉투) · exception(에러 코드) · config
└── infra/           외부 연동(OAuth, 카카오 로컬 API)
```

- **응답 봉투 하나, 에러 코드 하나.** 모든 응답은 `ApiResponse` 로 감싸고, 모든 실패는 `ErrorCode` 한 곳에서 상태·코드·문구가 정해집니다.
  예외 핸들러가 표준 Web 예외와 인가 실패까지 같은 봉투로 모읍니다
  ([`ErrorCode`](../backend/src/main/java/com/matradar/global/exception/ErrorCode.java),
  [`GlobalExceptionHandler`](../backend/src/main/java/com/matradar/global/exception/GlobalExceptionHandler.java)).
- **Redis 는 전용 Store 클래스로만 만집니다.** 서비스가 `RedisTemplate` 을 직접 부르지 않아 키 규칙과 TTL 이 한 파일에 모입니다.
  여러 명령이 원자적이어야 하는 카운터는 Lua 스크립트로 씁니다
  ([`RedisCounterScripts`](../backend/src/main/java/com/matradar/global/common/RedisCounterScripts.java)).
- **쿼리**는 단순한 건 Spring Data JPA, 조건이 붙는 검색·집계는 QueryDSL 로 씁니다. 위치 검색은 MySQL 공간 함수로 반경을 거릅니다.

### 인증

| 주체 | 방식 |
|---|---|
| 일반 사용자 | OAuth 2.0(Google) → **일회용 code** → 토큰 쌍 교환. 토큰을 URL 에 싣지 않습니다([`OAuthCodeStore`](../backend/src/main/java/com/matradar/global/auth/OAuthCodeStore.java)) |
| 게스트 | 가입 없이 둘러보는 읽기 전용 계정. 쓰기 API 는 서버가 403 으로 막고, 일정 기간 뒤 정리 배치가 지웁니다 |
| 관리자 | ID/PW 로그인. 실패 사유를 나누지 않고, 시도 횟수를 IP(IPv6 는 /64) 단위로 제한하고, **동시 세션은 1개**입니다([`JwtAuthenticationFilter`](../backend/src/main/java/com/matradar/global/auth/JwtAuthenticationFilter.java)) |

- Access token 은 짧게, refresh token 은 Redis 에 두고 재발급 때마다 교체합니다.
- 권한은 URL 매처와 메서드 보안(`@PreAuthorize`) **두 겹**으로 겁니다. 화면의 권한 가드는 그 앞의 UX 방어선이고, 최종 방어는 서버입니다.

## 프론트엔드

Expo(React Native) 하나의 코드베이스로 Web 을 먼저 운영하고, 같은 코드로 iOS·Android 빌드를 할 수 있게 유지합니다.
그래서 native 전용 라이브러리를 들이지 않고, 지도처럼 플랫폼마다 다른 것은 인터페이스 뒤로 숨깁니다.

```
app/              Expo Router 파일 기반 라우팅 — 홈 · 주변(지도) · 가게 상세 · 리뷰 작성 · 제보 · 마이 · 관리자
src/
├── api/          axios 클라이언트 + 도메인별 react-query hook · mappers(DTO → 도메인)
├── components/   화면 조각 + ui/(디자인 토큰)
├── hooks/        인증 · 위치 · 이탈 보호 …
├── stores/       zustand — 인증 · 작성 중 draft
├── lib/          에러 문구 매핑 · 지도 추상화 · 좌표 계산 · 화면 표시 변환
└── types/        dto/(백엔드 응답 1:1) + 도메인 모델과 zod 스키마
```

- **DTO / mapper / 도메인 3층.** `types/dto` 는 백엔드 응답 그대로, 화면은 도메인 모델만 씁니다. 누락·이상치 흡수와 이름 정리는 mapper 가
  혼자 맡습니다. 백엔드 필드가 바뀌면 고칠 곳이 mapper 하나입니다
  ([`mappers/place.ts`](../frontend/src/api/mappers/place.ts)).
- **봉투는 인터셉터 한 곳에서 벗깁니다.** 실패는 `ApiError(errorCode)` 로 던져 react-query 의 에러 경로로 흐르고, 사용자 문구는
  errorCode → 메시지 매핑 한 곳에서 정합니다. 401 은 refresh 후 재시도하되, 회복 불가능한 세션 축출은 그보다 먼저 가로챕니다
  ([`client.ts`](../frontend/src/api/client.ts)).
- **상태 분리.** 서버 데이터는 react-query, 화면 상태와 작성 중 draft 는 zustand. 섞지 않습니다.
- **작성 중인 입력을 잃게 만들지 않습니다.** 새로고침·탭 닫기와 앱 내부 이동을 둘 다 막고
  ([`useUnsavedDraftGuard`](../frontend/src/hooks/useUnsavedDraftGuard.ts)), 로그인 왕복이 필요한 흐름은 draft 를 저장해 둡니다.
- **mock(MSW)** 으로 백엔드 없이 화면을 만들 수 있지만, mock 은 백엔드 계약을 따라갑니다. 운영 빌드에는 mock 이 들어가지 않는지
  산출물을 열어 확인합니다([`build-web.js`](../frontend/scripts/build-web.js)).
