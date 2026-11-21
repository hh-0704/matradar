// 발췌 — mock 모드 헤더 부착 등 개발용 분기는 생략했다.
import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';

import { ErrorCode, type ApiResponse } from '@/types/api';

// success:false 응답을 일반 throw 로 흘려보내기 위한 에러. 사용자 문구 변환은 errorCode → 메시지 매핑 테이블의 책임.
export class ApiError extends Error {
  readonly errorCode: string;
  readonly status?: number;
  readonly data: unknown;

  constructor(
    errorCode: string,
    message: string,
    status?: number,
    data?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
    this.errorCode = errorCode;
    this.status = status;
    this.data = data;
  }
}

type TokenProvider = () => string | null | Promise<string | null>;
type UnauthorizedHandler = (
  original: InternalAxiosRequestConfig,
) => Promise<string | null>;
type SessionEvictedHandler = () => void | Promise<void>;

// 인증 store 에 직접 의존하지 않도록 provider 를 주입받는다 — 부팅 때 auth 모듈이 한 번 연결한다.
let getAccessToken: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = async () => null;
let onSessionEvicted: SessionEvictedHandler = () => {};

export function setAccessTokenProvider(provider: TokenProvider) {
  getAccessToken = provider;
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler;
}

// 서버에서 세션이 밀려난 401(AUTH_009) 처리 — refresh 로 회복할 수 없으므로 세션 정리 + 로그인 이동.
export function setSessionEvictedHandler(handler: SessionEvictedHandler) {
  onSessionEvicted = handler;
}

export const baseURL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

export const client: AxiosInstance = axios.create({
  baseURL,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
});

client.interceptors.request.use(async (config) => {
  const token = await getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

client.interceptors.response.use(
  // 성공 봉투는 여기서 벗긴다 — 화면은 봉투를 모른다.
  (response) => {
    const envelope = response.data as ApiResponse<unknown> | unknown;
    if (isApiResponse(envelope)) {
      if (envelope.success) {
        response.data = envelope.data;
        return response;
      }
      throw new ApiError(
        envelope.errorCode ?? ErrorCode.UNKNOWN,
        envelope.message ?? '',
        response.status,
        envelope.data,
      );
    }
    return response;
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as RetriableConfig | undefined;
    const isRefreshRequest = original?.url?.includes('/api/auth/refresh') ?? false;
    const env = error.response?.data;
    const errorCode = isApiResponse(env) ? env.errorCode : null;

    // 세션 축출(AUTH_009) — 관리자 동시 세션 1개 정책. 다른 기기에서 로그인하면 refresh token 까지 서버에서
    // 덮어써지므로 재발급은 반드시 실패한다. status 만 보는 아래 refresh 분기보다 **앞에** 두어
    // 헛된 왕복 없이 곧바로 세션 정리 + 로그인 이동. 이 분기 순서는 테스트로 고정돼 있다.
    const isSessionEvicted =
      error.response?.status === 401 &&
      errorCode === ErrorCode.AUTH_SESSION_EVICTED;

    if (isSessionEvicted) {
      await onSessionEvicted();
    } else if (
      error.response?.status === 401 &&
      original &&
      !original._retried &&
      !isRefreshRequest // refresh 요청 자체의 401 에 다시 refresh 하면 순환한다
    ) {
      original._retried = true;
      const newToken = await onUnauthorized(original);
      if (newToken) {
        original.headers.set('Authorization', `Bearer ${newToken}`);
        return client.request(original);
      }
    }

    if (isApiResponse(env) && env.errorCode) {
      throw new ApiError(
        env.errorCode,
        env.message ?? '',
        error.response?.status,
        env.data,
      );
    }

    if (error.code === 'ECONNABORTED') {
      throw new ApiError(ErrorCode.NETWORK_TIMEOUT, error.message);
    }
    if (!error.response) {
      throw new ApiError(ErrorCode.NETWORK_ERROR, error.message);
    }
    // 봉투 없이 raw HTTP status 만 온 경우(프록시 단 오류 등) — 매핑 테이블의 HTTP_{status} 문구로 떨어진다.
    throw new ApiError(`HTTP_${error.response.status}`, error.message, error.response.status);
  },
);

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  // 백엔드는 null 필드를 직렬화하지 않는다 — 성공 응답엔 errorCode/message 가, 실패 응답엔 data 가 통째로 빠진다.
  // 그래서 항상 있는 success(boolean) 하나로만 봉투를 판별한다. (data·errorCode 키까지 요구했던 초기 구현은
  // 실 연동에서 성공 unwrap 과 실패 errorCode 를 둘 다 놓쳤다.)
  return (
    typeof value === 'object' &&
    value !== null &&
    'success' in value &&
    typeof (value as { success: unknown }).success === 'boolean'
  );
}
