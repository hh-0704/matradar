package com.matradar.global.auth;

import com.matradar.domain.user.entity.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Access token 을 검증해 인증 주체를 세운다. 관리자 토큰은 <b>동시 세션 1개</b> 규칙을 함께 검사한다.
 *
 * <p>관리자가 다른 기기에서 로그인하면 이전 세션의 토큰은 서명이 유효해도 더 이상 현재 세션이 아니다.
 * 그 경우 인증을 세우지 않고 표시만 남겨 두면, 보호된 경로에서 entry point 가 401 {@code AUTH_009} 로 응답한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 세션 축출로 인증을 거부했음을 entry point 에 전달하는 request attribute.
     * 단순 미인증(401 {@code COMMON_003})과 갈라야 프론트가 refresh 재시도 없이 곧바로 재로그인을 띄운다.
     */
    static final String SESSION_EVICTED_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".SESSION_EVICTED";

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminSessionStore adminSessionStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null
                && jwtTokenProvider.validate(token)
                && jwtTokenProvider.isAccessToken(token)) {
            Long userId = jwtTokenProvider.parseUserId(token);
            String role = jwtTokenProvider.parseRole(token);
            String roleName = role != null ? role : UserRole.USER.name();

            if (isEvictedAdminSession(userId, roleName, token)) {
                log.debug("Evicted admin session for userId={}", userId);
                request.setAttribute(SESSION_EVICTED_ATTRIBUTE, Boolean.TRUE);
                chain.doFilter(request, response);
                return;
            }

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(UserRole.AUTHORITY_PREFIX + roleName);
            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }

    /**
     * 관리자 토큰의 세션 id 가 현재 세션과 다르면 밀려난 세션이다.
     * 관리자가 아닌 토큰은 Redis 를 조회하지 않고 통과시켜 일반 API 트래픽에 비용을 얹지 않는다.
     */
    private boolean isEvictedAdminSession(Long userId, String roleName, String token) {
        if (!UserRole.ADMIN.name().equals(roleName)) {
            return false;
        }
        return !adminSessionStore.isCurrent(userId, jwtTokenProvider.parseSessionId(token));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
