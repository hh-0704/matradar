// 별점 입력 — 이 서비스의 시그니처 UX. (발췌 — 누름·반짝임 애니메이션과 스타일 정의는 생략)
//
// - 기본값은 3점이다. 화면에 들어오면 3점이 선택된 채로 시작한다 — 별점 인플레이션을 입력 단계에서 누른다.
// - 별 색 자체가 메시지다. 1점 회색 → 3점 노랑 → 4점 골드 그라데이션·글로우 → 5점 무지개·반짝임.
//   색·라벨·설명은 디자인 토큰(tokens.rating) 한 곳에 있다.
// - 4~5점을 고르면 화면이 즉시 경고를 띄운다(이 컴포넌트 밖, 리뷰 작성 화면의 몫).
// - 입력용이다. 리뷰 카드의 별 표시는 별도 표시용 컴포넌트가 맡는다 — readOnly prop 하나로 분기하지 않는다.
import { useState } from 'react';
import {
  Platform,
  Text,
  View,
  type AccessibilityActionEvent,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { tokens, type Rating } from '@/components/ui/tokens';

export type StarRatingProps = {
  value?: Rating;
  defaultValue?: Rating;
  onChange?: (next: Rating) => void;
  size?: number;
  style?: StyleProp<ViewStyle>;
  testID?: string;
};

const RATINGS: readonly Rating[] = [1, 2, 3, 4, 5];

export function StarRating({
  value,
  defaultValue = 3,
  onChange,
  size = 40,
  style,
  testID,
}: StarRatingProps) {
  const isControlled = value !== undefined;
  const [internal, setInternal] = useState<Rating>(defaultValue);
  const current = isControlled ? (value as Rating) : internal;
  const tone = tokens.rating[current];

  const apply = (next: Rating) => {
    if (current === next) return;
    if (!isControlled) setInternal(next);
    onChange?.(next);
  };

  // 키보드 조작 — 화살표 · Home/End · 숫자키.
  const onKeyDown = (e: KeyboardEvent) => {
    const key = e.key;
    if (key === 'ArrowRight' || key === 'ArrowUp') {
      if (current < 5) {
        e.preventDefault();
        apply((current + 1) as Rating);
      }
    } else if (key === 'ArrowLeft' || key === 'ArrowDown') {
      if (current > 1) {
        e.preventDefault();
        apply((current - 1) as Rating);
      }
    } else if (key === 'Home') {
      e.preventDefault();
      apply(1);
    } else if (key === 'End') {
      e.preventDefault();
      apply(5);
    } else if (/^[1-5]$/.test(key)) {
      e.preventDefault();
      apply(Number(key) as Rating);
    }
  };

  // 스크린리더 조작 — adjustable role 의 increment/decrement.
  const onAccessibilityAction = (event: AccessibilityActionEvent) => {
    const action = event.nativeEvent.actionName;
    if (action === 'increment' && current < 5) apply((current + 1) as Rating);
    else if (action === 'decrement' && current > 1) apply((current - 1) as Rating);
  };

  const webProps =
    Platform.OS === 'web'
      ? ({ tabIndex: 0, focusable: true, onKeyDown } as Record<string, unknown>)
      : {};

  return (
    <View style={[styles.container, style]} testID={testID}>
      {/* 별 5개를 묶은 한 덩어리가 하나의 조절 가능한 컨트롤이다 — 별마다 포커스를 갖지 않는다. */}
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="별점"
        accessibilityValue={{ min: 1, max: 5, now: current, text: `${current}점, ${tone.label}` }}
        accessibilityHint="좌우 화살표 키로 별점을 조절할 수 있어요"
        accessibilityActions={[
          { name: 'increment', label: '별점 올리기' },
          { name: 'decrement', label: '별점 내리기' },
        ]}
        onAccessibilityAction={onAccessibilityAction}
        style={styles.row}
        {...webProps}
      >
        {RATINGS.map((position) => (
          <StarButton
            key={position}
            position={position}
            active={position <= current}
            currentRating={current}
            size={size}
            onPress={() => apply(position)}
          />
        ))}
      </View>
      <Text style={styles.labelLine}>
        <Text style={styles.score}>{current}점</Text>
        <Text style={styles.divider}> · </Text>
        <Text style={styles.label}>{tone.label}</Text>
      </Text>
      {/* 고른 점수의 뜻을 바로 보여준다 — "3점 = 맛있게 먹음, 재방문 의사 있음" */}
      <Text style={styles.description}>{tone.description}</Text>
    </View>
  );
}

// … StarButton(누름 축소 · 5점 반짝임 애니메이션) 생략

function StarGlyph({ active, currentRating, size }: { active: boolean; currentRating: Rating; size: number }) {
  const tone = tokens.rating[currentRating];

  if (!active) {
    // 선택하지 않은 별은 연한 아웃라인 — 뒤로 물러나 보이게.
    return <Text style={[styles.glyph, { fontSize: size, color: tokens.colors.border.strong }]}>☆</Text>;
  }

  // 1~3점: 단색.
  if (currentRating <= 3) {
    return <Text style={[styles.glyph, styles.glyphShadow, { fontSize: size, color: tone.star }]}>★</Text>;
  }

  // 4~5점: web 은 텍스트 그라데이션 + drop-shadow 글로우. native 는 단색으로 의도적으로 낮춘다.
  if (Platform.OS === 'web') {
    return (
      <Text
        style={[
          styles.glyph,
          { fontSize: size },
          webGradientStyle(tone.gradient, tone.gradientAngleDeg),
          webGlowStyle(tone.glow),
        ]}
      >
        ★
      </Text>
    );
  }
  return <Text style={[styles.glyph, { fontSize: size, color: tone.star }]}>★</Text>;
}

function webGradientStyle(gradient: readonly string[] | null, angleDeg: number): StyleProp<TextStyle> {
  if (!gradient) return undefined;
  return {
    backgroundImage: `linear-gradient(${angleDeg}deg, ${gradient.join(', ')})`,
    WebkitBackgroundClip: 'text',
    backgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    color: 'transparent',
  } as unknown as TextStyle;
}

function webGlowStyle(
  glow: { readonly color: string; readonly radius: number; readonly opacity: number } | null,
): StyleProp<TextStyle> {
  if (!glow) return undefined;
  const alpha = Math.round(glow.opacity * 255).toString(16).padStart(2, '0').toUpperCase();
  return { filter: `drop-shadow(0 0 ${glow.radius}px ${glow.color}${alpha})` } as unknown as TextStyle;
}

// … styles 생략
