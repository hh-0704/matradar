package com.matradar.global.common;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 고정 창(fixed window) 카운터의 Redis Lua 스크립트 — IP 단위 제한들이 함께 쓴다.
 *
 * <p><b>왜 Lua 인가.</b> {@code INCR} 뒤에 {@code EXPIRE} 를 따로 보내면, 두 명령 사이에 프로세스가 죽거나
 * 연결이 끊겼을 때 <b>키가 만료 없이 남는다.</b> 그 카운터는 영원히 줄지 않아 해당 IP 가 영구 차단된다.
 * 스크립트는 Redis 안에서 한 번에 실행되므로 그 틈이 없다.
 */
public final class RedisCounterScripts {

    /**
     * {@code KEYS[1]} 을 1 올리고 올린 뒤의 값을 돌려준다. 키에 만료가 없으면 {@code ARGV[1]}(밀리초)로 건다.
     *
     * <ul>
     *   <li><b>고정 창 유지</b> — 만료는 키가 처음 생길 때만 걸린다. 이미 만료가 있는 키는 건드리지 않아
     *       창이 요청마다 뒤로 밀리지 않는다.</li>
     *   <li><b>만료 없는 키 복구</b> — 예전 방식에서 만료가 빠진 채 남은 키({@code PTTL == -1})를 다음 증가 때
     *       한 창 길이로 묶는다. 값은 그대로 두므로 이미 상한을 넘긴 키는 그 창 동안 계속 막히고, 창이 지나면
     *       풀린다 — 복구 때문에 상한이 한꺼번에 풀리지 않는다.</li>
     * </ul>
     */
    public static final RedisScript<Long> INCREMENT_IN_WINDOW = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /**
     * {@code KEYS[1]} 이 있고 값이 양수일 때만 1 내린다. 내린 뒤의 값, 아무것도 하지 않았으면 {@code -1}.
     *
     * <p>그냥 {@code DECR} 하면 창이 끝나 키가 사라진 뒤 호출됐을 때 <b>만료 없는 음수 키</b>를 새로 만든다 —
     * 그 키는 다음 창부터 상한을 그만큼 늘리고, 만료가 없어 사라지지도 않는다.
     */
    public static final RedisScript<Long> DECREMENT_IF_POSITIVE = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]))
            if current == nil or current <= 0 then
                return -1
            end
            return redis.call('DECR', KEYS[1])
            """, Long.class);

    private RedisCounterScripts() {
    }
}
