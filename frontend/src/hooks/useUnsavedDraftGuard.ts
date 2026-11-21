import { useNavigation } from 'expo-router';
import { useEffect } from 'react';
import { Platform } from 'react-native';

import { confirmAsync } from '@/lib/confirm';

// 작성 중(dirty)인 화면의 이탈 보호. 입력 유실은 "나중에 다듬을 것" 이 아니라 기능 결함으로 본다.
// - web 새로고침·탭 닫기 → beforeunload 네이티브 confirm.
// - SPA 내부 이동(탭 전환·뒤로가기·router.push) → React Navigation 'beforeRemove' → confirmAsync.
//   native 의 하드웨어 뒤로가기도 'beforeRemove' 로 들어와 같은 분기로 커버된다.
// 둘 중 하나만 걸면 반드시 빠지는 경로가 생긴다.
//
// 제출 중(isSubmittingRef.current)에는 막지 않는다 — 성공 후의 화면 이동에 confirm 이 또 뜨지 않게.
export function useUnsavedDraftGuard({
  isDirty,
  isSubmittingRef,
  message,
}: {
  isDirty: boolean;
  isSubmittingRef: { current: boolean };
  message: string;
}) {
  const navigation = useNavigation();

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    if (!isDirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      if (isSubmittingRef.current) return;
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [isDirty, isSubmittingRef]);

  useEffect(() => {
    if (!isDirty) return;
    // 이동을 일단 막고(preventDefault), 확인을 받으면 원래 action 을 다시 발화한다.
    const unsub = navigation.addListener('beforeRemove', (e) => {
      if (isSubmittingRef.current) return;
      e.preventDefault();
      void confirmAsync(message).then((ok) => {
        if (ok) navigation.dispatch(e.data.action);
      });
    });
    return unsub;
  }, [navigation, isDirty, isSubmittingRef, message]);
}
