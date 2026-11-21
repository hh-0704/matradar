// 주변 탭 — 지도가 멈출 때(idle) 검색 좌표를 바꿀지 정한다.
//
// 규칙
//   - 아직 검색한 적이 없으면(위치 미허용·미응답): 지도를 처음 띄운 중심에서 **사실상 움직이지 않은** idle 은 무시한다.
//     지도 SDK 가 mount 직후 스스로 내는 idle 이 이것이다 — 위치를 거부한 사용자가 기본 좌표로 "몰래 검색" 당하지
//     않게(사용자 행동이 있어야 조회한다). 사용자가 옮긴 뒤의 idle 은 거리와 상관없이 검색한다.
//   - 검색한 적이 있으면: 마지막 검색 좌표에서 임계 거리 이상 옮겼을 때만 다시 검색한다(빈 호출 방지).
//
// 예전 규칙은 "첫 idle 1회 무시" 였다. 그런데 idle 리스너가 지도 생성 → 상태 반영 → 다음 렌더의 effect 에서야 붙어서,
// 자동 idle 이 그 전에 지나가면 **무시 대상이 사용자의 첫 이동으로 밀렸다**(운영에서 관찰 — 첫 드래그엔 핀이 안 뜨고
// 두 번째부터 떴다). 이벤트 순서가 아니라 좌표로 판단해, 자동 idle 이 언제 오든 아예 안 오든 결과가 같게 했다.
// 그 대가로 위치가 없을 때 줌만 바꾸는 것(중심 그대로)은 검색하지 않는다 — 아직 볼 곳을 고르지 않은 상태라 의도와 맞다.

import { distanceMeters } from '@/lib/geo/haversine';
import type { Coord } from '@/lib/map/types';

/** 검색한 뒤 다시 검색하려면 옮겨야 하는 거리. */
export const MAP_MOVE_THRESHOLD_M = 500;

/**
 * "움직이지 않음" 으로 보는 거리 — 좌표 → 지도 내부 좌표 → 좌표 왕복 오차를 흡수한다.
 * 사용자가 일부러 옮긴 거리로 보기엔 충분히 작다.
 */
export const UNMOVED_EPSILON_M = 10;

/** 검색 좌표를 바꿔야 하면 새 좌표를, 아니면 `null` 을 돌려준다. */
export function nextSearchCoordOnIdle({
  idleCenter,
  searchCoord,
  initialCenter,
}: {
  idleCenter: Coord;
  searchCoord: Coord | null;
  initialCenter: Coord;
}): Coord | null {
  if (searchCoord === null) {
    return distanceMeters(initialCenter, idleCenter) < UNMOVED_EPSILON_M ? null : idleCenter;
  }
  return distanceMeters(searchCoord, idleCenter) >= MAP_MOVE_THRESHOLD_M ? idleCenter : null;
}
