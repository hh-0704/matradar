package com.matradar.global.auth;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * IP 기준 제한의 클라이언트 키 — <b>IPv4 는 주소 그대로, IPv6 는 /64 로 묶는다.</b>
 *
 * <p>IPv6 사용자는 보통 /64(주소 2^64개)를 통째로 받는다. 주소 하나 단위로 세면 그 안에서 주소를 바꿔 가며
 * 보내는 것만으로 IP당 제한이 무력해진다. 관리자 로그인 시도 제한처럼 IP 단위 방어에 기대는 곳일수록 영향이 크다.
 * nginx 의 요청 제한도 같은 기준으로 묶어, 두 층의 키가 어긋나지 않게 했다.
 *
 * <p>키에만 쓴다 — 로그에는 원래 주소를 그대로 남긴다. 파싱은 {@link InetAddress#ofLiteral} 로 하므로
 * <b>DNS 조회가 일어나지 않는다</b>(리터럴이 아니면 예외 → 원래 값을 그대로 키로 쓴다). 같은 /64 라면 어떤 표기
 * (압축·전체·대문자·앞자리 0·대괄호)든 같은 키가 되고, IPv4-mapped IPv6({@code ::ffff:a.b.c.d})는 IPv4 로 본다.
 */
public final class RateLimitClientKey {

    private RateLimitClientKey() {
    }

    public static String of(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
            return remoteAddr;
        }
        String literal = remoteAddr.strip();
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        int zone = literal.indexOf('%');
        if (zone >= 0) {
            literal = literal.substring(0, zone);
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(literal);
        } catch (IllegalArgumentException notALiteral) {
            return remoteAddr;
        }
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        byte[] b = address.getAddress();
        return String.format("%x:%x:%x:%x::/64",
                group(b, 0), group(b, 2), group(b, 4), group(b, 6));
    }

    private static int group(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }
}
