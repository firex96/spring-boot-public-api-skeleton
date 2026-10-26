# Integration Guide

> Public API Skeleton 클라이언트 연동 가이드

이 문서의 예제는 이 레포의 서버 코드(`src/main/kotlin/com/rex/api/pub/skeleton/`)와 같은 경로·상태 코드·응답 모델을 씁니다. 엔드포인트 상세는 [API Reference](API_REFERENCE.md) 를 봅니다.

## 목차

- [시작하기 전에](#시작하기-전에)
- [Web Application 연동](#web-application-연동)
- [Mobile Application 연동](#mobile-application-연동)
- [Third-Party Service 연동](#third-party-service-연동)
- [OAuth2 Client 연동](#oauth2-client-연동)
- [소셜 로그인 연동](#소셜-로그인-연동)
- [SDK 및 라이브러리](#sdk-및-라이브러리)

---

## 시작하기 전에

### API 기본 정보

| 항목 | 값 |
|------|-----|
| Base URL (로컬) | `http://localhost:8080` |
| Base URL (배포 환경) | 파생 프로젝트가 정합니다. 아래 예제는 `https://api.example.com` 을 자리표시로 씁니다 |
| 경로 접두사 | 없습니다. context-path·`/api/v1` 접두사 없이 `/auth/login`, `/users/me` 처럼 부릅니다 |
| Content-Type | `application/json` (`/oauth2/token`·`/oauth2/introspect`·`/oauth2/revoke` 는 `application/x-www-form-urlencoded`) |
| 인증 방식 | Bearer Token (JWT) |

### 필수 헤더

```http
Content-Type: application/json
Authorization: Bearer {accessToken}
```

`X-User-Id` 헤더는 보내지 않습니다. 서버가 감사 주체로 쓰는 opt-in 헤더(`app.audit.trust-user-header`, 기본 꺼짐)이고, 인증된 요청의 주체는 access token 이 정합니다.

### 인증 API 한눈에 보기

| 메서드·경로 | 요청 본문 | 응답 |
|---|---|---|
| `POST /auth/signup` | `{loginId, password, email?, name?, nickname?, phone?}` — `loginId` 4–100자, `password` 8–100자 | **201** `AuthUserInfoModel` |
| `POST /auth/login` | `{loginId, password, deviceType?, deviceInfo?}` | **200** `TokenResponseModel` 또는 **202** `MfaChallengeModel` |
| `POST /auth/login/mfa` | `{mfaToken, method, code}` — `method` 는 `"totp"` 또는 `"recovery"` | 200 `TokenResponseModel` |
| `POST /auth/refresh` | `{refreshToken}` | 200 `AccessTokenResponseModel` (새 refresh token 없음) |
| `POST /auth/logout` | 없음 (Bearer 필요) | **204** 빈 본문 |
| `GET /users/me` | — (Bearer 필요) | 200 `AuthUserInfoModel` |
| `POST /auth/password/change` | `{currentPassword, newPassword}` (Bearer 필요) | 204 |
| `DELETE /users/me/sessions` | — (Bearer 필요) — 현재 세션을 뺀 내 세션 전부 종료 | 204 |
| `DELETE /users/me/sessions/{sessionId}` | — (Bearer 필요) — 내 세션 하나 종료, 남의 세션은 403 | 204 |

`deviceType`·`deviceInfo` 는 선택값이며 로그인 세션에 기록됩니다(`deviceType` 은 20자 이하 자유 문자열).

### 응답 형식

**성공 응답:** 래퍼 없이 각 API 의 응답 모델을 그대로 돌려줍니다.

로그인 성공(200, `TokenResponseModel`):
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

2차 인증이 켜진 계정의 로그인 1단계 통과(202, `MfaChallengeModel`). `methods` 는 그 계정이 쓸 수 있는 방법이며, 남은 복구 코드가 없으면 `["totp"]` 만 옵니다.
```json
{
  "mfaToken": "…",
  "expiresIn": 300,
  "methods": ["totp", "recovery"]
}
```

토큰 갱신(200, `AccessTokenResponseModel`). **refresh token 은 회전하지 않습니다** — 응답에 refresh token 이 없으니 클라이언트는 저장해 둔 refresh token 을 그대로 씁니다.
```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

내 정보(`AuthUserInfoModel`, `GET /users/me`·`POST /auth/signup`). 식별자 필드 이름은 `id` 입니다. `email`·`name`·`nickname`·`phone`·`profileImage` 는 `null` 일 수 있습니다.
```json
{
  "id": 1,
  "loginId": "user01",
  "email": "user01@example.com",
  "name": null,
  "nickname": "홍길동",
  "phone": null,
  "profileImage": null,
  "groups": ["USER"]
}
```

**에러 응답:** `ErrorResponseModel` 입니다(`docs/ERROR_CODES.md`). `reason` 은 사용자에게 그대로 보여도 되는 문장이고, `errors` 는 검증 실패일 때 필드별 목록(`field`·`value`·`reason`)입니다.
```json
{
  "code": "NOT_VALID_ERROR",
  "reason": "email: 올바른 형식의 이메일 주소여야 합니다",
  "errors": null,
  "status": 400,
  "divisionCode": "G011",
  "resultMessage": "One or more validation errors occurred"
}
```

`/oauth2/*` 인가 서버 엔드포인트의 오류는 RFC 6749 형식(`error`, `error_description`, `error_uri`)으로 옵니다.

### 401 재시도 규칙 (모든 클라이언트 공통)

- 보호된 API 가 401 을 주면 `POST /auth/refresh` 로 access token 을 다시 받고 **원래 요청을 한 번만** 재시도합니다.
- `/auth/login`, `/auth/login/mfa`, `/auth/refresh` 의 401 은 갱신·재시도하지 않습니다. 자격 오류이거나 갱신 자체가 실패한 것이라, 갱신하면 루프가 됩니다.
- 재시도한 요청이 다시 401 이면 포기하고 토큰을 지운 뒤 로그인 화면으로 보냅니다.

---

## Web Application 연동

### React/Next.js 연동

#### 1. 모델 타입

```typescript
// lib/api-types.ts
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/** POST /auth/refresh — refresh token 은 오지 않는다(회전 없음) */
export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export type MfaMethod = 'totp' | 'recovery';

/** POST /auth/login 의 202 응답 */
export interface MfaChallenge {
  mfaToken: string;
  expiresIn: number;
  methods: MfaMethod[];
}

export type LoginResult =
  | { kind: 'signedIn' }
  | { kind: 'mfaRequired'; challenge: MfaChallenge };

/** GET /users/me · POST /auth/signup — id 는 서버의 Long */
export interface User {
  id: number;
  loginId: string;
  email?: string | null;
  name?: string | null;
  nickname?: string | null;
  phone?: string | null;
  profileImage?: string | null;
  groups: string[];
}

export interface SignupRequest {
  loginId: string;
  password: string;
  email?: string;
  name?: string;
  nickname?: string;
  phone?: string;
}

export interface ErrorResponse {
  code: string;
  reason?: string | null;
  errors?: { field: string; value: string; reason?: string | null }[] | null;
  status: number;
  divisionCode: string;
  resultMessage: string;
}
```

#### 2. API Client 설정

```typescript
// lib/api-client.ts
import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import type { AccessTokenResponse } from '@/lib/api-types';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

// 이 경로의 401 은 갱신·재시도하지 않는다 — 자격 오류이거나 갱신 자체의 실패다
const NO_REFRESH_PATHS = ['/auth/login', '/auth/login/mfa', '/auth/refresh'];

const REQUEST_TIMEOUT_MS = 10_000;

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

export const tokenStore = {
  getAccessToken: () => localStorage.getItem('access_token'),
  getRefreshToken: () => localStorage.getItem('refresh_token'),
  setTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  },
  setAccessToken(accessToken: string) {
    localStorage.setItem('access_token', accessToken);
  },
  clear() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
  },
};

class ApiClient {
  private client: AxiosInstance;
  private refreshPromise: Promise<string> | null = null;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      headers: { 'Content-Type': 'application/json' },
      timeout: REQUEST_TIMEOUT_MS, // axios 기본값은 0(무제한) — 멈춘 요청이 갱신 대기까지 묶지 않게
    });
    this.setupInterceptors();
  }

  private setupInterceptors() {
    // Request interceptor - 토큰 자동 추가
    this.client.interceptors.request.use((config) => {
      const token = tokenStore.getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });

    // Response interceptor - 401 이면 한 번만 갱신 후 재시도
    this.client.interceptors.response.use(
      (response) => response,
      async (error: AxiosError) => {
        const original = error.config as RetriableConfig | undefined;
        if (error.response?.status !== 401 || !original || NO_REFRESH_PATHS.includes(original.url ?? '')) {
          throw error;
        }
        // 루프 가드 — 재시도한 요청이 다시 401 이면 세션이 끝난 것이다
        if (original._retried) {
          this.handleSessionExpired();
          throw error;
        }

        original._retried = true;
        let accessToken: string;
        try {
          accessToken = await this.refreshAccessToken();
        } catch (refreshError) {
          this.handleSessionExpired();
          throw refreshError;
        }
        original.headers.Authorization = `Bearer ${accessToken}`;
        // 재시도도 이 인터셉터를 지난다 — 다시 401 이면 위의 _retried 분기가 정리한다
        return this.client(original);
      }
    );
  }

  // 동시에 여러 요청이 401 을 받아도 갱신은 한 번만 한다
  private refreshAccessToken(): Promise<string> {
    if (!this.refreshPromise) {
      this.refreshPromise = (async () => {
        const refreshToken = tokenStore.getRefreshToken();
        if (!refreshToken) {
          throw new Error('No refresh token');
        }
        // 인터셉터를 타지 않는 별도 호출 — 갱신 실패가 다시 갱신을 부르지 않는다
        const { data } = await axios.post<AccessTokenResponse>(
          `${API_BASE_URL}/auth/refresh`,
          { refreshToken },
          { timeout: REQUEST_TIMEOUT_MS },
        );
        // 응답에 refresh token 이 없다 — 저장된 refresh token 은 그대로 둔다
        tokenStore.setAccessToken(data.accessToken);
        return data.accessToken;
      })().finally(() => {
        this.refreshPromise = null;
      });
    }
    return this.refreshPromise;
  }

  private handleSessionExpired() {
    tokenStore.clear();
    window.location.href = '/login';
  }

  // API Methods
  async get<T>(url: string, params?: object): Promise<T> {
    const response = await this.client.get<T>(url, { params });
    return response.data;
  }

  async post<T>(url: string, data?: object): Promise<T> {
    const response = await this.client.post<T>(url, data);
    return response.data;
  }

  /** 상태 코드로 분기해야 하는 응답(로그인 200/202)용 */
  async postWithStatus<T>(url: string, data?: object): Promise<{ status: number; data: T }> {
    const response = await this.client.post<T>(url, data);
    return { status: response.status, data: response.data };
  }

  async put<T>(url: string, data?: object): Promise<T> {
    const response = await this.client.put<T>(url, data);
    return response.data;
  }

  async delete<T>(url: string): Promise<T> {
    const response = await this.client.delete<T>(url);
    return response.data;
  }
}

export const apiClient = new ApiClient();
```

API 체인은 CORS 를 열어 두므로(`SecurityConfiguration.corsConfigurationSource`) 다른 출처의 SPA 도 Bearer 토큰으로 이 클라이언트를 쓸 수 있습니다. 소셜 로그인의 웹 흐름만 같은 출처가 필요합니다([소셜 로그인 연동](#web-application-소셜-로그인) 참고).

#### 3. Auth Service 구현

```typescript
// services/auth-service.ts
import { apiClient, tokenStore } from '@/lib/api-client';
import type { LoginResult, MfaChallenge, MfaMethod, SignupRequest, TokenResponse, User } from '@/lib/api-types';

export const authService = {
  /** 200 이면 토큰 저장, 202 면 2차 인증 challenge 를 돌려준다 */
  async login(loginId: string, password: string): Promise<LoginResult> {
    const { status, data } = await apiClient.postWithStatus<TokenResponse | MfaChallenge>('/auth/login', {
      loginId,
      password,
      deviceType: 'WEB',
    });

    if (status === 202) {
      return { kind: 'mfaRequired', challenge: data as MfaChallenge };
    }

    const tokens = data as TokenResponse;
    tokenStore.setTokens(tokens.accessToken, tokens.refreshToken);
    return { kind: 'signedIn' };
  },

  /** 202 로 받은 mfaToken 과 사용자가 입력한 코드로 로그인을 마친다 */
  async completeMfa(mfaToken: string, method: MfaMethod, code: string): Promise<void> {
    const tokens = await apiClient.post<TokenResponse>('/auth/login/mfa', { mfaToken, method, code });
    tokenStore.setTokens(tokens.accessToken, tokens.refreshToken);
  },

  /** 204 빈 본문. 호출이 실패해도 토큰은 지운다 */
  async logout(): Promise<void> {
    try {
      await apiClient.post<void>('/auth/logout');
    } finally {
      tokenStore.clear();
    }
  },

  async getCurrentUser(): Promise<User> {
    return apiClient.get<User>('/users/me');
  },

  /** 201 — 가입한 사용자 정보. 토큰은 주지 않으므로 이어서 login 을 부른다 */
  async signup(data: SignupRequest): Promise<User> {
    return apiClient.post<User>('/auth/signup', data);
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await apiClient.post<void>('/auth/password/change', { currentPassword, newPassword });
  },

  isAuthenticated(): boolean {
    return !!tokenStore.getAccessToken();
  },
};
```

#### 4. Auth Context (React)

```tsx
// contexts/AuthContext.tsx
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { tokenStore } from '@/lib/api-client';
import type { LoginResult, MfaMethod, User } from '@/lib/api-types';
import { authService } from '@/services/auth-service';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (loginId: string, password: string) => Promise<LoginResult>;
  completeMfa: (mfaToken: string, method: MfaMethod, code: string) => Promise<void>;
  logout: () => Promise<void>;
  /** 저장된 토큰으로 GET /users/me 를 다시 읽는다 — 소셜 콜백 등 토큰을 밖에서 받은 뒤 부른다 */
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!authService.isAuthenticated()) {
      setUser(null);
      return;
    }
    try {
      setUser(await authService.getCurrentUser());
    } catch {
      // 갱신까지 실패한 경우 — 인터셉터가 토큰을 지운다
      tokenStore.clear();
      setUser(null);
    }
  }, []);

  useEffect(() => {
    refreshUser().finally(() => setIsLoading(false));
  }, [refreshUser]);

  const login = async (loginId: string, password: string) => {
    const result = await authService.login(loginId, password);
    if (result.kind === 'signedIn') {
      await refreshUser();
    }
    return result;
  };

  const completeMfa = async (mfaToken: string, method: MfaMethod, code: string) => {
    await authService.completeMfa(mfaToken, method, code);
    await refreshUser();
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        login,
        completeMfa,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
```

#### 5. 로그인 화면 — 202(2차 인증) 분기

```tsx
// app/login/page.tsx
'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import { useAuth } from '@/contexts/AuthContext';
import type { ErrorResponse, MfaChallenge } from '@/lib/api-types';

export default function LoginPage() {
  const { login, completeMfa } = useAuth();
  const router = useRouter();
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null);
  const [error, setError] = useState<string | null>(null);

  const toMessage = (e: unknown) =>
    axios.isAxiosError<ErrorResponse>(e) ? e.response?.data?.reason ?? '요청을 처리하지 못했습니다' : '요청을 처리하지 못했습니다';

  const onPassword = async (loginId: string, password: string) => {
    try {
      const result = await login(loginId, password);
      if (result.kind === 'mfaRequired') {
        setChallenge(result.challenge); // 코드 입력 화면으로 전환
      } else {
        router.replace('/');
      }
    } catch (e) {
      setError(toMessage(e));
    }
  };

  const onMfaCode = async (code: string, useRecovery: boolean) => {
    if (!challenge) return;
    try {
      await completeMfa(challenge.mfaToken, useRecovery ? 'recovery' : 'totp', code);
      router.replace('/');
    } catch (e) {
      // mfaToken 은 expiresIn 초 뒤 만료된다 — 만료됐으면 처음부터 다시 로그인한다
      setError(toMessage(e));
    }
  };

  // 폼 마크업은 생략 — challenge 가 null 이면 아이디/비밀번호 폼, 아니면 코드 입력 폼을 그린다
  return null;
}
```

#### 6. Protected Route

```tsx
// components/ProtectedRoute.tsx
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { useEffect } from 'react';

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
}
```

### Vue.js 연동

HTTP 클라이언트와 타입은 위 React 절의 `lib/api-client.ts`·`lib/api-types.ts` 를 그대로 씁니다(프레임워크와 무관합니다). 환경변수만 `import.meta.env.VITE_API_URL` 로 바꿉니다.

#### 1. Composable 설정

```typescript
// composables/useAuth.ts
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiClient, tokenStore } from '@/lib/api-client';
import type { MfaChallenge, MfaMethod, TokenResponse, User } from '@/lib/api-types';

const user = ref<User | null>(null);
const isLoading = ref(true);
const mfaChallenge = ref<MfaChallenge | null>(null);

export function useAuth() {
  const router = useRouter();

  const isAuthenticated = computed(() => !!user.value);

  const fetchUser = async () => {
    if (!tokenStore.getAccessToken()) {
      user.value = null;
      isLoading.value = false;
      return;
    }
    try {
      user.value = await apiClient.get<User>('/users/me');
    } catch {
      tokenStore.clear();
      user.value = null;
    } finally {
      isLoading.value = false;
    }
  };

  /** true 면 로그인 완료, false 면 mfaChallenge 에 담긴 2차 인증을 이어서 해야 한다 */
  const login = async (loginId: string, password: string): Promise<boolean> => {
    const { status, data } = await apiClient.postWithStatus<TokenResponse | MfaChallenge>('/auth/login', {
      loginId,
      password,
      deviceType: 'WEB',
    });

    if (status === 202) {
      mfaChallenge.value = data as MfaChallenge;
      return false;
    }

    const tokens = data as TokenResponse;
    tokenStore.setTokens(tokens.accessToken, tokens.refreshToken);
    await fetchUser();
    return true;
  };

  const completeMfa = async (method: MfaMethod, code: string) => {
    if (!mfaChallenge.value) {
      throw new Error('No MFA challenge');
    }
    const tokens = await apiClient.post<TokenResponse>('/auth/login/mfa', {
      mfaToken: mfaChallenge.value.mfaToken,
      method,
      code,
    });
    tokenStore.setTokens(tokens.accessToken, tokens.refreshToken);
    mfaChallenge.value = null;
    await fetchUser();
  };

  const logout = async () => {
    try {
      await apiClient.post<void>('/auth/logout'); // 204 빈 본문
    } finally {
      tokenStore.clear();
      user.value = null;
      router.push('/login');
    }
  };

  return {
    user,
    isLoading,
    isAuthenticated,
    mfaChallenge,
    login,
    completeMfa,
    logout,
    fetchUser,
  };
}
```

---

## Mobile Application 연동

### iOS (Swift) 연동

#### 1. API Client 설정

```swift
// APIClient.swift
import Foundation

actor APIClient {
    static let shared = APIClient()

    private let baseURL = "https://api.example.com" // 로컬은 http://localhost:8080
    /// 요청마다 유한한 시간 제한을 둔다 — 멈춘 갱신이 기다리는 요청을 모두 묶지 않게
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration)
    }()
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    /// 이 경로의 401 은 갱신·재시도하지 않는다
    private static let noRefreshPaths: Set<String> = ["/auth/login", "/auth/login/mfa", "/auth/refresh"]

    /// 진행 중인 갱신 — 동시에 401 을 받은 요청들이 갱신 한 번을 함께 기다린다
    private var refreshTask: Task<String, Error>?

    private init() {}

    // MARK: - Token Management

    private var accessToken: String? {
        get { KeychainManager.shared.get("access_token") }
        set {
            if let token = newValue {
                KeychainManager.shared.save("access_token", value: token)
            } else {
                KeychainManager.shared.delete("access_token")
            }
        }
    }

    private var refreshToken: String? {
        get { KeychainManager.shared.get("refresh_token") }
        set {
            if let token = newValue {
                KeychainManager.shared.save("refresh_token", value: token)
            } else {
                KeychainManager.shared.delete("refresh_token")
            }
        }
    }

    private func saveTokens(_ tokens: TokenResponse) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    private func clearTokens() {
        accessToken = nil
        refreshToken = nil
    }

    // MARK: - Request Methods

    /// 응답 본문을 해석하지 않고 돌려준다. 401 이면 한 번만 갱신 후 재시도한다.
    private func send(
        _ endpoint: String,
        method: String = "GET",
        body: (any Encodable)? = nil,
        requiresAuth: Bool = true,
        isRetry: Bool = false
    ) async throws -> (Data, HTTPURLResponse) {
        var urlRequest = URLRequest(url: URL(string: "\(baseURL)\(endpoint)")!)
        urlRequest.httpMethod = method

        if let body {
            urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
            urlRequest.httpBody = try encoder.encode(body)
        }
        if requiresAuth, let token = accessToken {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await session.data(for: urlRequest)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        if httpResponse.statusCode == 401,
           requiresAuth,
           !isRetry, // 루프 가드
           !Self.noRefreshPaths.contains(endpoint) {
            _ = try await refreshAccessToken()
            return try await send(endpoint, method: method, body: body, requiresAuth: requiresAuth, isRetry: true)
        }
        // 재시도한 요청도 401 이면 세션이 끝난 것이다 — 토큰을 지우고 로그인으로 보낸다
        if httpResponse.statusCode == 401, requiresAuth, isRetry {
            clearTokens()
            throw APIError.notAuthenticated
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let errorResponse = try? decoder.decode(ErrorResponse.self, from: data)
            throw APIError.serverError(
                status: httpResponse.statusCode,
                code: errorResponse?.divisionCode ?? "UNKNOWN",
                message: errorResponse?.reason ?? errorResponse?.resultMessage ?? "요청을 처리하지 못했습니다"
            )
        }
        return (data, httpResponse)
    }

    func request<T: Decodable>(
        _ endpoint: String,
        method: String = "GET",
        body: (any Encodable)? = nil,
        requiresAuth: Bool = true
    ) async throws -> T {
        let (data, _) = try await send(endpoint, method: method, body: body, requiresAuth: requiresAuth)
        return try decoder.decode(T.self, from: data)
    }

    /// 204 No Content — 빈 본문을 디코딩하지 않는다
    func requestNoContent(
        _ endpoint: String,
        method: String = "POST",
        body: (any Encodable)? = nil
    ) async throws {
        _ = try await send(endpoint, method: method, body: body)
    }

    // MARK: - Token Refresh

    private func refreshAccessToken() async throws -> String {
        if let refreshTask {
            return try await refreshTask.value
        }
        guard let refreshToken else {
            throw APIError.notAuthenticated
        }

        // 저장까지 공유 작업 안에서 끝낸다 — 기다리던 요청이 어떤 순서로 재개돼도 새 토큰을 읽는다
        // (actor 는 대기자의 재개 순서를 보장하지 않는다)
        let task = Task { () throws -> String in
            defer { self.refreshTask = nil }
            do {
                let response: AccessTokenResponse = try await self.request(
                    "/auth/refresh",
                    method: "POST",
                    body: RefreshRequest(refreshToken: refreshToken),
                    requiresAuth: false
                )
                // 응답에 refresh token 이 없다(회전하지 않는다) — 저장된 refresh token 은 그대로 둔다
                self.accessToken = response.accessToken
                return response.accessToken
            } catch {
                self.clearTokens()
                throw error
            }
        }
        refreshTask = task
        return try await task.value
    }

    // MARK: - Auth Methods

    /// 200 이면 토큰을 저장하고 사용자 정보를, 202 면 2차 인증 challenge 를 돌려준다
    func login(loginId: String, password: String) async throws -> LoginResult {
        let (data, httpResponse) = try await send(
            "/auth/login",
            method: "POST",
            body: LoginRequest(loginId: loginId, password: password, deviceType: "IOS", deviceInfo: nil),
            requiresAuth: false
        )
        return try await handleLoginResponse(data: data, statusCode: httpResponse.statusCode)
    }

    /// 202 로 받은 mfaToken 과 코드로 로그인을 마친다
    func completeMfa(mfaToken: String, method: MfaMethod, code: String) async throws -> User {
        let tokens: TokenResponse = try await request(
            "/auth/login/mfa",
            method: "POST",
            body: MfaLoginRequest(mfaToken: mfaToken, method: method, code: code),
            requiresAuth: false
        )
        saveTokens(tokens)
        return try await getCurrentUser()
    }

    /// 비밀번호 로그인과 네이티브 소셜 로그인이 같은 200/202 계약을 쓴다
    func handleLoginResponse(data: Data, statusCode: Int) async throws -> LoginResult {
        switch statusCode {
        case 202:
            return .mfaRequired(try decoder.decode(MfaChallenge.self, from: data))
        default:
            saveTokens(try decoder.decode(TokenResponse.self, from: data))
            return .signedIn(try await getCurrentUser())
        }
    }

    /// 204 빈 본문. 호출이 실패해도 토큰은 지운다
    func logout() async {
        defer { clearTokens() }
        try? await requestNoContent("/auth/logout")
    }

    func getCurrentUser() async throws -> User {
        try await request("/users/me")
    }

    /// 201 — 가입한 사용자 정보. 토큰은 주지 않으므로 이어서 login 을 부른다
    func signup(_ body: SignupRequest) async throws -> User {
        try await request("/auth/signup", method: "POST", body: body, requiresAuth: false)
    }
}

// MARK: - Models

struct LoginRequest: Encodable {
    let loginId: String
    let password: String
    let deviceType: String?
    let deviceInfo: String?
}

struct SignupRequest: Encodable {
    let loginId: String
    let password: String
    var email: String? = nil
    var name: String? = nil
    var nickname: String? = nil
    var phone: String? = nil
}

struct RefreshRequest: Encodable {
    let refreshToken: String
}

enum MfaMethod: String, Codable {
    case totp
    case recovery
}

struct MfaLoginRequest: Encodable {
    let mfaToken: String
    let method: MfaMethod
    let code: String
}

struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let tokenType: String
    let expiresIn: Int64
}

/// POST /auth/refresh — refreshToken 필드가 없다
struct AccessTokenResponse: Decodable {
    let accessToken: String
    let tokenType: String
    let expiresIn: Int64
}

struct MfaChallenge: Decodable {
    let mfaToken: String
    let expiresIn: Int64
    let methods: [MfaMethod]
}

enum LoginResult {
    case signedIn(User)
    case mfaRequired(MfaChallenge)
}

struct User: Decodable {
    let id: Int64
    let loginId: String
    let email: String?
    let name: String?
    let nickname: String?
    let phone: String?
    let profileImage: String?
    let groups: [String]
}

struct ErrorResponse: Decodable {
    let code: String
    let reason: String?
    let status: Int
    let divisionCode: String
    let resultMessage: String
}

enum APIError: Error {
    case invalidResponse
    case notAuthenticated
    case serverError(status: Int, code: String, message: String)
}
```

### Android (Kotlin) 연동

#### 1. Retrofit 설정

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

인증 전 호출(로그인·2차 인증·갱신·가입·소셜 로그인)은 토큰을 붙이지 않는 `AuthApi`, 인증이 필요한 호출은 `ApiService` 로 나눕니다.

```kotlin
// AuthApi.kt — 토큰 없이 부르는 엔드포인트. 갱신 전용 클라이언트에 묶는다
interface AuthApi {
    /** 200 TokenResponse 또는 202 MfaChallenge — 본문 모양이 달라 원문으로 받는다 */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ResponseBody>

    @POST("auth/login/mfa")
    suspend fun completeMfa(@Body request: MfaLoginRequest): Response<TokenResponse>

    /** Authenticator 가 OkHttp 스레드에서 동기로 부른다 */
    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<AccessTokenResponse>

    /** 201 AuthUserInfoModel */
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<UserResponse>

    /** 네이티브 소셜 로그인 — provider 인가 코드. 200/202 는 login 과 같다 */
    @POST("auth/oauth2/{provider}/login")
    suspend fun socialLogin(
        @Path("provider") provider: String,
        @Body request: SocialLoginRequest,
    ): Response<ResponseBody>
}

// ApiService.kt — Bearer 가 필요한 엔드포인트
interface ApiService {
    /** 204 빈 본문 */
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("users/me")
    suspend fun getCurrentUser(): Response<UserResponse>

    @POST("auth/password/change")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    @DELETE("users/me/sessions")
    suspend fun logoutOtherSessions(): Response<Unit>
}

// Models
data class LoginRequest(
    val loginId: String,
    val password: String,
    val deviceType: String? = "ANDROID",
    val deviceInfo: String? = null,
)

data class SignupRequest(
    val loginId: String,
    val password: String,
    val email: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val phone: String? = null,
)

data class RefreshRequest(
    val refreshToken: String,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class SocialLoginRequest(
    val code: String,
    val deviceType: String? = "ANDROID",
    val deviceInfo: String? = null,
)

enum class MfaMethod {
    @SerializedName("totp") TOTP,
    @SerializedName("recovery") RECOVERY,
}

data class MfaLoginRequest(
    val mfaToken: String,
    val method: MfaMethod,
    val code: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

/** POST /auth/refresh — refreshToken 이 없다(회전하지 않는다) */
data class AccessTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

data class MfaChallenge(
    val mfaToken: String,
    val expiresIn: Long,
    val methods: List<MfaMethod>,
)

data class UserResponse(
    val id: Long,
    val loginId: String,
    val email: String?,
    val name: String?,
    val nickname: String?,
    val phone: String?,
    val profileImage: String?,
    val groups: List<String>,
)

data class ErrorResponse(
    val code: String,
    val reason: String?,
    val status: Int,
    val divisionCode: String,
    val resultMessage: String,
)

class ApiException(val status: Int, val error: ErrorResponse?) :
    Exception(error?.reason ?: error?.resultMessage ?: "HTTP $status") {
    companion object {
        fun from(response: Response<*>, gson: Gson = Gson()): ApiException =
            ApiException(
                response.code(),
                response.errorBody()?.string()?.let { runCatching { gson.fromJson(it, ErrorResponse::class.java) }.getOrNull() },
            )
    }
}
```

#### 2. Token Interceptor

```kotlin
// AuthInterceptor.kt
class AuthInterceptor(
    private val tokenManager: TokenManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val accessToken = tokenManager.getAccessToken()
            ?: return chain.proceed(chain.request())

        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        return chain.proceed(request)
    }
}

// TokenAuthenticator.kt — OkHttp 가 401 을 받으면 부른다
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        // 이 경로의 401 은 갱신·재시도하지 않는다
        if (response.request.url.encodedPath in NO_REFRESH_PATHS) {
            return null
        }
        // 루프 가드 — 응답 사슬에 앞선 401 이 있으면(갱신 후 재시도한 요청) 세션이 끝난 것이다.
        // priorResponse 에는 리다이렉트(3xx)도 들어 있어, 401 만 센다 — 302 → 401 은 첫 401 이라 갱신한다
        if (generateSequence(response.priorResponse) { it.priorResponse }.any { it.code == 401 }) {
            tokenManager.clearTokens()
            return null
        }

        synchronized(this) {
            // 다른 요청이 그 사이 갱신했으면 새 토큰으로 재시도만 한다
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = tokenManager.getAccessToken()
            if (currentToken != null && currentToken != sentToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshToken() ?: return null
            val refreshed = try {
                authApi.refresh(RefreshRequest(refreshToken)).execute()
            } catch (e: IOException) {
                return null // 네트워크 오류 — 토큰은 지우지 않는다
            }

            val body = refreshed.body()
            if (!refreshed.isSuccessful || body == null) {
                tokenManager.clearTokens() // 갱신 실패 - 로그아웃
                return null
            }

            // refresh token 은 그대로 두고 access token 만 바꾼다
            tokenManager.saveAccessToken(body.accessToken)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${body.accessToken}")
                .build()
        }
    }

    companion object {
        private val NO_REFRESH_PATHS = setOf("/auth/login", "/auth/login/mfa", "/auth/refresh")
    }
}
```

#### 3. API Client 설정

```kotlin
// ApiClient.kt
class ApiClient(context: Context) {
    private val baseUrl = "https://api.example.com/" // 에뮬레이터에서 로컬 서버는 http://10.0.2.2:8080/

    val tokenManager = TokenManager(context)

    // 본문은 로그에 남기지 않는다 — 로그인·가입·2차 인증 요청의 비밀번호·코드와 응답 토큰이 기록된다.
    // 헤더도 디버그 빌드에서만, Authorization 은 가린다.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    // Auth API — 토큰을 붙이지 않고 Authenticator 도 없다. 인증 본문을 다루므로 로깅하지 않는다
    val authApi: AuthApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    // Main API — Bearer 를 붙이고 401 이면 갱신한다
    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
                .addInterceptor(loggingInterceptor)
                .authenticator(TokenAuthenticator(tokenManager, authApi))
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

// TokenManager.kt
class TokenManager(context: Context) {
    // 운영 앱은 EncryptedSharedPreferences 등 암호화 저장소를 쓴다
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString("access_token", accessToken).apply()
    }

    fun clearTokens() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }
}
```

#### 4. 로그인·2차 인증·로그아웃

```kotlin
// AuthRepository.kt
sealed interface LoginResult {
    data object SignedIn : LoginResult
    data class MfaRequired(val challenge: MfaChallenge) : LoginResult
}

class AuthRepository(
    private val client: ApiClient,
    private val gson: Gson = Gson(),
) {
    suspend fun login(loginId: String, password: String): LoginResult =
        handleLoginResponse(client.authApi.login(LoginRequest(loginId, password)))

    /** 202 로 받은 mfaToken 과 코드로 로그인을 마친다 */
    suspend fun completeMfa(mfaToken: String, method: MfaMethod, code: String) {
        val response = client.authApi.completeMfa(MfaLoginRequest(mfaToken, method, code))
        val tokens = response.body() ?: throw ApiException.from(response, gson)
        client.tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
    }

    suspend fun getCurrentUser(): UserResponse {
        val response = client.apiService.getCurrentUser()
        return response.body() ?: throw ApiException.from(response, gson)
    }

    /** 204 빈 본문. 호출이 실패해도 토큰은 지운다 */
    suspend fun logout() {
        try {
            client.apiService.logout()
        } finally {
            client.tokenManager.clearTokens()
        }
    }

    /** 비밀번호 로그인과 네이티브 소셜 로그인이 같은 200/202 계약을 쓴다 */
    fun handleLoginResponse(response: Response<ResponseBody>): LoginResult {
        if (!response.isSuccessful) throw ApiException.from(response, gson)
        val raw = response.body()?.string() ?: throw ApiException(response.code(), null)
        return when (response.code()) {
            202 -> LoginResult.MfaRequired(gson.fromJson(raw, MfaChallenge::class.java))
            else -> {
                val tokens = gson.fromJson(raw, TokenResponse::class.java)
                client.tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
                LoginResult.SignedIn
            }
        }
    }
}
```

---

## Third-Party Service 연동

### Server-to-Server 연동

서버 간 통신에는 **Client Credentials Flow**를 사용합니다. 사용자가 없는 토큰이며, 이 스켈레톤 자신의 API(`/admin/**` 포함)는 이 토큰을 받지 않습니다 — 1st-party API 는 `/auth/login` 계열 토큰(사용자 세션)만 인증합니다. 이 토큰은 **별도의 자원 서버**가 받아서 인가 서버에 검사를 맡기는 용도입니다.

```
호출 서비스 ──(1) POST /oauth2/token (client_credentials)──▶ 인가 서버(이 스켈레톤)
호출 서비스 ──(2) Authorization: Bearer {access_token}──────▶ 자원 서버(파생 서비스)
자원 서버   ──(3) POST /oauth2/introspect (자원 서버 자신의 자격)──▶ 인가 서버
```

#### 1. 클라이언트 등록

관리자가 `POST /admin/oauth2/clients` 로 클라이언트를 등록하고 `client_id`와 `client_secret`을 발급합니다. 호출 서비스용 클라이언트에는 `client_credentials` grant 와 필요한 scope 를 줍니다. 자원 서버도 자기 클라이언트를 등록해 두고, 그 자격으로 introspection 을 부릅니다.

#### 2. Access Token 발급

```bash
curl -X POST https://api.example.com/oauth2/token \
  -u "your-client-id:your-client-secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "scope=your.scope"
```

클라이언트 인증은 `client_secret_basic`(위의 `-u`) 또는 `client_secret_post`(`client_id`·`client_secret` 폼 필드)입니다. `scope` 는 클라이언트에 등록된 범위 안이어야 하며, 생략하면 등록된 범위 전체로 발급합니다.

**응답:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "your.scope"
}
```

#### 3. 자원 서버에서 토큰 검사

access token 은 HMAC(대칭키)으로 서명됩니다. 그래서 `/.well-known/jwks.json` 의 공개키로는 **검증할 수 없습니다** — JWKS 의 RS256 키는 OIDC ID 토큰 서명용입니다. 자원 서버는 자기 클라이언트 자격으로 introspection 을 부릅니다.

```bash
curl -X POST https://api.example.com/oauth2/introspect \
  -u "resource-server-client-id:resource-server-client-secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token={access_token}"
```

**응답** (필드 이름은 camelCase 입니다. client_credentials 토큰은 사용자가 없어 `username`·`sub` 가 `null` 입니다):
```json
{
  "active": true,
  "scope": "your.scope",
  "clientId": "your-client-id",
  "username": null,
  "tokenType": "Bearer",
  "exp": 1767225600,
  "iat": 1767222000,
  "nbf": null,
  "sub": null,
  "aud": "your-client-id",
  "iss": "https://api.example.com",
  "jti": "…"
}
```

`active` 가 `false` 이면(만료·폐기·알 수 없는 토큰) 자원 서버는 401 로 거절합니다. 토큰 폐기는 `POST /oauth2/revoke` 입니다.

#### 4. Python 예시

```python
import requests
from datetime import datetime, timedelta

TIMEOUT_SECONDS = 10  # requests 는 기본으로 무제한 대기한다 — 모든 호출에 둔다


class ClientCredentialsClient:
    """호출 서비스 — 토큰을 받아 자원 서버를 부른다"""

    def __init__(self, auth_base_url: str, client_id: str, client_secret: str, scope: str | None = None):
        self.auth_base_url = auth_base_url
        self.client_id = client_id
        self.client_secret = client_secret
        self.scope = scope
        self.access_token: str | None = None
        self.token_expires_at = datetime.min

    def _get_token(self) -> str:
        """토큰 발급 또는 캐시된 토큰 반환"""
        if self.access_token and self.token_expires_at > datetime.now():
            return self.access_token

        data = {"grant_type": "client_credentials"}
        if self.scope:
            data["scope"] = self.scope

        response = requests.post(
            f"{self.auth_base_url}/oauth2/token",
            data=data,
            auth=(self.client_id, self.client_secret),  # client_secret_basic
            timeout=TIMEOUT_SECONDS,
        )
        response.raise_for_status()

        body = response.json()
        self.access_token = body["access_token"]
        self.token_expires_at = datetime.now() + timedelta(seconds=body["expires_in"] - 60)  # 1분 여유
        return self.access_token

    def request(self, method: str, url: str, **kwargs) -> requests.Response:
        """자원 서버(파생 서비스) 호출 — 이 스켈레톤의 API 가 아니다"""
        headers = kwargs.pop("headers", {})
        headers["Authorization"] = f"Bearer {self._get_token()}"
        kwargs.setdefault("timeout", TIMEOUT_SECONDS)
        response = requests.request(method, url, headers=headers, **kwargs)
        response.raise_for_status()
        return response


def introspect(auth_base_url: str, client_id: str, client_secret: str, token: str) -> dict:
    """자원 서버 쪽 — 받은 Bearer 토큰을 인가 서버에 검사시킨다"""
    response = requests.post(
        f"{auth_base_url}/oauth2/introspect",
        data={"token": token},
        auth=(client_id, client_secret),
        timeout=TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return response.json()  # {"active": bool, "scope": ..., "clientId": ..., ...}


# 사용 예시
caller = ClientCredentialsClient(
    auth_base_url="https://api.example.com",
    client_id="your-client-id",
    client_secret="your-client-secret",
)
caller.request("GET", "https://resource.example.com/reports")
```

---

## OAuth2 Client 연동

사용자 인증이 필요한 Third-Party 앱은 **Authorization Code Flow (+ PKCE)** 를 사용합니다.

**토큰 교환은 반드시 앱의 백엔드에서 합니다.** 이 인가 서버는 모든 클라이언트에 `client_secret` 을 요구하고(public 클라이언트 유형이 없습니다), `/oauth2/token` 은 시크릿 없는 요청을 `invalid_client` 로 거절합니다. 시크릿을 브라우저 JS 나 앱 번들에 넣을 수 없으므로 **브라우저만으로 교환하는 구성(SPA 단독)은 지원하지 않습니다.** PKCE 는 시크릿을 대신하지 않고 그 위에 더합니다.

### 연동 흐름

```
1. 클라이언트 등록 → client_id, client_secret 발급, redirect_uri(앱 백엔드 콜백) 등록
2. 브라우저 → 앱 백엔드 /login 시작
3. 앱 백엔드가 state·code_verifier 를 만들어 자기 세션에 두고, 브라우저를 /oauth2/authorize 로 리다이렉트
4. 사용자 로그인(/oauth2/login) 및 동의(/oauth2/consent) — 인가 서버 화면
5. 인가 서버 → 브라우저 → 앱 백엔드 콜백 (code, state)
6. 앱 백엔드가 state 를 검증하고 client_secret + code_verifier 로 /oauth2/token 교환
7. 토큰은 앱 백엔드가 보관하고, 브라우저와는 앱 자신의 세션 쿠키로 통신
```

### 1. 앱 백엔드 — 인가 요청 시작

```typescript
// server/oauth.ts (Node.js / Express + express-session)
import crypto from 'node:crypto';
import express from 'express';

const AUTH_SERVER = 'https://api.example.com';
const CLIENT_ID = process.env.OAUTH_CLIENT_ID!;
const CLIENT_SECRET = process.env.OAUTH_CLIENT_SECRET!; // 백엔드에만 둔다
const REDIRECT_URI = 'https://your-app.com/oauth/callback';

const base64Url = (buf: Buffer) => buf.toString('base64url');

export const router = express.Router();

router.get('/oauth/login', (req, res) => {
  const state = base64Url(crypto.randomBytes(32));
  const codeVerifier = base64Url(crypto.randomBytes(32));
  const codeChallenge = base64Url(crypto.createHash('sha256').update(codeVerifier).digest());

  req.session.oauth = { state, codeVerifier };

  const url = new URL(`${AUTH_SERVER}/oauth2/authorize`);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('client_id', CLIENT_ID);
  url.searchParams.set('redirect_uri', REDIRECT_URI);
  url.searchParams.set('scope', 'openid profile email');
  url.searchParams.set('state', state);
  url.searchParams.set('code_challenge', codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');

  res.redirect(url.toString());
});
```

### 2. 앱 백엔드 — Callback 처리와 토큰 교환

```typescript
router.get('/oauth/callback', async (req, res) => {
  const { code, state, error } = req.query as Record<string, string | undefined>;
  const saved = req.session.oauth;
  delete req.session.oauth; // state·verifier 는 한 번만 쓴다

  if (error) {
    // 쿼리 값을 응답에 되돌리지 않는다(반사형 XSS) — 원인은 서버 로그에만 남긴다
    console.warn('OAuth authorize failed', { error });
    return res.status(400).type('text/plain').send('인가에 실패했습니다.');
  }
  if (!saved || !code || state !== saved.state) {
    return res.status(400).type('text/plain').send('Invalid state');
  }

  const tokenResponse = await fetch(`${AUTH_SERVER}/oauth2/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64'),
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: REDIRECT_URI,
      code_verifier: saved.codeVerifier,
    }),
    signal: AbortSignal.timeout(10_000), // fetch 는 기본으로 무제한 대기한다
  });

  if (!tokenResponse.ok) {
    // RFC 6749 오류: { error, error_description }
    return res.status(502).type('text/plain').send('토큰 교환 실패');
  }

  const tokens = await tokenResponse.json();
  // {
  //   "access_token": "...",
  //   "token_type": "Bearer",
  //   "expires_in": 3600,
  //   "refresh_token": "...",   // 발급된 경우
  //   "scope": "openid profile email",
  //   "id_token": "..."         // openid scope 요청 시
  // }

  req.session.tokens = tokens; // 브라우저로 내려보내지 않는다
  res.redirect('/');
});
```

### 3. 받은 토큰의 쓰임

- `GET /oauth2/userinfo` — `openid` scope 를 받은 access token 으로 사용자 정보를 조회합니다.
- `id_token` — RS256 으로 서명되며 `/.well-known/jwks.json` 의 공개키로 검증합니다.
- access token 은 HMAC 서명이라 JWKS 로 검증할 수 없습니다. 자원 서버는 [`/oauth2/introspect`](#3-자원-서버에서-토큰-검사) 로 검사합니다.
- `/users/me` 등 이 스켈레톤의 1st-party API 는 이 토큰을 받지 않습니다(`/auth/login` 계열 토큰 전용).
- 갱신은 백엔드에서 `grant_type=refresh_token` 으로 `/oauth2/token` 을 부릅니다(클라이언트 인증 동일).

---

## 소셜 로그인 연동

지원 provider 는 `google`, `kakao`, `naver` 이고, 서버 설정(`oauth2.<provider>`)에서 켠 provider 만 동작합니다. 흐름은 두 가지입니다.

| 흐름 | 진입 | 토큰 전달 |
|---|---|---|
| 웹(브라우저) | `GET /auth/oauth2/{provider}/authorize` | 서버 콜백 → 고정 콜백 페이지 → `POST /auth/session/token` (세션 쿠키 + CSRF) |
| 네이티브 앱 | 앱이 provider 인가 코드를 직접 받음 | `POST /auth/oauth2/{provider}/login` `{code}` → 200 토큰 또는 202 2차 인증 |

### Web Application 소셜 로그인

#### 흐름

```
1. 브라우저 → GET /auth/oauth2/{provider}/authorize   (서버가 이 브라우저 세션에 묶인 state 를 만들고 provider 로 302)
2. provider 로그인·동의
3. provider → GET /auth/oauth2/{provider}/callback    (서버가 state 검증·코드 교환, 브라우저 세션 로그인)
4. 서버 → 302 /demo/auth/callback                    (URL 에 토큰 없음)
     - 실패 시: /demo/auth/callback?error=…&error_description=…
     - 2차 인증이 켜진 계정: /auth/mfa (서버 화면에서 코드 입력) → 완료 후 /demo/auth/callback
5. 콜백 페이지 → POST /auth/session/token             (SKELETON_SESSION 쿠키 + CSRF 헤더, 한 번만 성공)
     → 200 TokenResponseModel
```

#### 제약 — React 앱에 그대로 붙지 않습니다

- 4단계의 리다이렉트 대상은 서버 상수 `BrowserSecurityPaths.DEMO_CALLBACK` = `/demo/auth/callback` 으로 **고정**되어 있고, 그 경로는 스켈레톤의 Thymeleaf 데모 페이지(`templates/demo/auth/callback.html`)입니다.
- `/auth/oauth2/*/authorize`·`/callback`·`/auth/session/**` 는 브라우저 체인(세션 쿠키 + CSRF)이고 **CORS 가 꺼져 있습니다.** 다른 출처의 SPA 는 `POST /auth/session/token` 을 부를 수 없고, **같은 출처에서만** 동작합니다.
- CSRF 토큰은 서버가 렌더링한 페이지에 심어 줍니다(데모 페이지는 `_csrf` 를 `data-token`·`data-header` 로 넣습니다). 이를 따로 조회하는 API 는 없습니다.

그래서 React 앱에서 웹 소셜 로그인을 쓰려면 파생 프로젝트가 서버를 바꿔야 합니다.

1. 앱과 API 를 같은 출처로 서비스합니다(리버스 프록시 등).
2. 콜백 리다이렉트 대상(`DEMO_CALLBACK`)을 앱의 콜백 경로로 바꾸고, 그 페이지에 CSRF 토큰과 헤더 이름을 서버가 넣어 줍니다. 아래 예시는 `<meta name="_csrf">`·`<meta name="_csrf_header">` 로 받는다고 가정합니다.

이 변경 없이 쓸 수 있는 것은 스켈레톤의 `/demo/auth/callback` 페이지뿐입니다. 다른 출처의 SPA 라면 [OAuth2 Client 연동](#oauth2-client-연동)처럼 앱 백엔드를 두는 구성을 검토합니다.

#### 1. 소셜 로그인 버튼 구현

```tsx
// components/SocialLogin.tsx
const SOCIAL_PROVIDERS = {
  google: {
    name: 'Google',
    icon: '/icons/google.svg',
    bgColor: '#fff',
    textColor: '#757575',
    border: '1px solid #ddd',
  },
  kakao: {
    name: '카카오',
    icon: '/icons/kakao.svg',
    bgColor: '#FEE500',
    textColor: '#000',
    border: 'none',
  },
  naver: {
    name: '네이버',
    icon: '/icons/naver.svg',
    bgColor: '#03C75A',
    textColor: '#fff',
    border: 'none',
  },
};

export function SocialLogin() {
  const handleSocialLogin = (provider: string) => {
    // 로그인 후 돌아올 앱 안 경로 (같은 출처)
    sessionStorage.setItem('social_login_return_url', window.location.pathname + window.location.search);

    // 같은 출처의 서버 경로로 이동 — 서버가 state 를 만들고 provider 로 보낸다
    window.location.href = `/auth/oauth2/${provider}/authorize`;
  };

  return (
    <div className="social-login">
      {Object.entries(SOCIAL_PROVIDERS).map(([key, provider]) => (
        <button
          key={key}
          onClick={() => handleSocialLogin(key)}
          style={{
            backgroundColor: provider.bgColor,
            color: provider.textColor,
            border: provider.border,
          }}
        >
          <img src={provider.icon} alt={provider.name} />
          <span>{provider.name}로 로그인</span>
        </button>
      ))}
    </div>
  );
}
```

#### 2. Callback 처리

위 「제약」의 서버 변경을 마쳐 서버가 이 페이지로 리다이렉트하고 CSRF 메타 태그를 넣어 준다는 전제입니다.

```tsx
// app/auth/callback/page.tsx
'use client';
import { useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { tokenStore } from '@/lib/api-client';
import type { TokenResponse } from '@/lib/api-types';

export default function SocialCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { refreshUser } = useAuth();
  const handled = useRef(false); // 토큰 교환은 한 번만 성공한다 — StrictMode 이중 실행 방지

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const fail = (message: string) => {
      alert(`로그인 실패: ${message}`);
      router.replace('/login');
    };

    const handleCallback = async () => {
      // 실패는 쿼리로 온다 — 토큰은 URL 에 오지 않는다
      const error = searchParams.get('error');
      if (error) {
        // 쿼리 문구는 누구나 만들 수 있다 — 화면에는 고정 문장만 보인다
        console.warn('social login failed', error);
        return fail('소셜 로그인에 실패했습니다. 다시 시도해 주세요.');
      }

      const csrfToken = document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content;
      const csrfHeader = document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')?.content;
      if (!csrfToken || !csrfHeader) {
        return fail('CSRF 토큰이 없습니다');
      }

      // 콜백이 서버에 맡겨 둔 토큰을 세션 쿠키 + CSRF 헤더로 한 번 꺼낸다
      const response = await fetch('/auth/session/token', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { [csrfHeader]: csrfToken },
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) {
        return fail('토큰을 받지 못했습니다. 소셜 로그인을 다시 시도해 주세요.');
      }

      const tokens: TokenResponse = await response.json();
      tokenStore.setTokens(tokens.accessToken, tokens.refreshToken);
      await refreshUser();

      // 원래 페이지로 복귀
      const returnUrl = sessionStorage.getItem('social_login_return_url') || '/';
      sessionStorage.removeItem('social_login_return_url');
      router.replace(returnUrl);
    };

    // 타임아웃·네트워크 오류는 reject 로 온다 — 잡지 않으면 화면이 「처리 중」에 멈춘다
    handleCallback().catch((e) => {
      console.warn('social login token exchange failed', e);
      fail('소셜 로그인을 마치지 못했습니다. 다시 시도해 주세요.');
    });
  }, [searchParams, router, refreshUser]);

  return <div>로그인 처리 중...</div>;
}
```

### Mobile 소셜 로그인 (네이티브)

네이티브 앱은 provider **인가 코드**를 받아 `POST /auth/oauth2/{provider}/login` 에 `{code, deviceType?, deviceInfo?}` 로 보냅니다. 응답은 비밀번호 로그인과 같습니다 — 200 `TokenResponseModel` 또는 202 `MfaChallengeModel`(이어서 `POST /auth/login/mfa`).

**지원하지 않는 것:** provider 의 access token 이나 ID 토큰을 서버에 넘기는 로그인은 없습니다. Google Sign-In SDK 의 `idToken`, Kakao SDK `loginWithKakaoTalk`·`loginWithKakaoAccount` 가 돌려주는 카카오 토큰은 이 서버가 받지 않습니다.

**코드의 조건:** 서버는 코드를 교환할 때 자신의 provider `client_id`·`client_secret` 과 `redirect_uri = {oauth2.redirect-base-url}/auth/oauth2/{provider}/callback` 을 보냅니다(`client/social/*OAuth2Client.kt`). 그래서 앱은

- 서버와 **같은 provider client_id** 로,
- **같은 redirect_uri** 로 인가 요청을 만들고,
- provider 가 그 redirect_uri 로 돌려보낼 때 브라우저가 서버로 가기 전에 앱이 가로채서 `code` 를 꺼내야 합니다.

다른 redirect_uri(커스텀 스킴 등)나 provider SDK 가 따로 발급한 코드는 교환 단계에서 provider 가 거절합니다. 이 흐름에서는 서버가 state 를 발급하지 않으므로 **state 생성·검증은 앱의 책임**입니다.

#### iOS (Swift) - Google 로그인

iOS 17.4 이상의 `ASWebAuthenticationSession` 은 https 콜백을 가로챌 수 있습니다(콜백 도메인의 associated domains 설정 필요).

```swift
import AuthenticationServices

final class SocialLoginManager: NSObject, ASWebAuthenticationPresentationContextProviding {
    private let apiHost = "api.example.com"            // oauth2.redirect-base-url 의 호스트
    private let googleClientId = "SERVER_GOOGLE_CLIENT_ID" // 서버 설정 oauth2.google.client-id 와 같은 값

    func signInWithGoogle() async throws -> LoginResult {
        let state = UUID().uuidString
        let callbackPath = "/auth/oauth2/google/callback"

        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: googleClientId),
            URLQueryItem(name: "redirect_uri", value: "https://\(apiHost)\(callbackPath)"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "state", value: state),
        ]

        let callbackURL: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: components.url!,
                callback: .https(host: apiHost, path: callbackPath)
            ) { url, error in
                if let url { continuation.resume(returning: url) }
                else { continuation.resume(throwing: error ?? SocialLoginError.cancelled) }
            }
            session.presentationContextProvider = self
            session.start()
        }

        let items = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard items.first(where: { $0.name == "state" })?.value == state else {
            throw SocialLoginError.invalidState // state 검증은 앱 책임
        }
        guard let code = items.first(where: { $0.name == "code" })?.value else {
            throw SocialLoginError.noCode
        }

        return try await APIClient.shared.socialLogin(provider: "google", code: code)
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        ASPresentationAnchor()
    }
}

enum SocialLoginError: Error {
    case cancelled
    case invalidState
    case noCode
}

// APIClient extension
extension APIClient {
    /// 200 이면 토큰 저장 후 사용자 정보, 202 면 2차 인증 challenge — completeMfa 로 마친다
    func socialLogin(provider: String, code: String) async throws -> LoginResult {
        struct SocialLoginRequest: Encodable {
            let code: String
            let deviceType: String?
        }

        let (data, httpResponse) = try await send(
            "/auth/oauth2/\(provider)/login",
            method: "POST",
            body: SocialLoginRequest(code: code, deviceType: "IOS"),
            requiresAuth: false
        )
        return try await handleLoginResponse(data: data, statusCode: httpResponse.statusCode)
    }
}
```

`send` 는 `private` 이므로 이 extension 은 `APIClient.swift` 파일 안에 둡니다.

#### Android (Kotlin) - Kakao 로그인

Kakao SDK 의 로그인 함수는 카카오 토큰을 돌려주므로 쓰지 않습니다. Custom Tabs 로 카카오 인가 화면을 열고, 콜백 URL 을 Android App Links 로 앱이 받습니다(서버 도메인의 `/.well-known/assetlinks.json` 과 앱의 `autoVerify` intent-filter 필요). App Link 검증이 안 되면 브라우저가 서버 콜백으로 가고, 서버는 자기가 발급한 state 가 아니므로 `invalid_state` 오류로 끝냅니다.

```kotlin
// KakaoLoginManager.kt
class KakaoLoginManager(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) {
    private val apiHost = "api.example.com"            // oauth2.redirect-base-url 의 호스트
    private val kakaoClientId = "SERVER_KAKAO_CLIENT_ID" // 서버 설정 oauth2.kakao.client-id 와 같은 값
    private var pendingState: String? = null

    /** 1단계 — 카카오 인가 화면을 연다 */
    fun start(activity: Activity) {
        val state = UUID.randomUUID().toString().also { pendingState = it }
        val uri = Uri.parse("https://kauth.kakao.com/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", kakaoClientId)
            .appendQueryParameter("redirect_uri", "https://$apiHost/auth/oauth2/kakao/callback")
            .appendQueryParameter("state", state)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(activity, uri)
    }

    /** 2단계 — App Link 로 받은 콜백 URI 를 처리한다 (Activity.onNewIntent 등에서 부른다) */
    suspend fun handleCallback(callback: Uri): Result<LoginResult> = runCatching {
        val expectedState = pendingState.also { pendingState = null }
        require(expectedState != null && callback.getQueryParameter("state") == expectedState) {
            "state 가 맞지 않습니다" // state 검증은 앱 책임
        }
        callback.getQueryParameter("error")?.let { error("카카오 로그인 실패: $it") }
        val code = requireNotNull(callback.getQueryParameter("code")) { "인가 코드가 없습니다" }

        // 200 이면 토큰 저장, 202 면 MfaRequired — authRepository.completeMfa 로 마친다
        authRepository.handleLoginResponse(
            authApi.socialLogin(provider = "kakao", request = SocialLoginRequest(code = code)),
        )
    }
}
```

---

## SDK 및 라이브러리

### 공식 SDK

현재 공식 SDK는 제공되지 않습니다. 위의 예시 코드를 참고하여 직접 구현하시기 바랍니다.

### 추천 라이브러리

| 플랫폼 | 라이브러리 | 용도 |
|--------|-----------|------|
| JavaScript | Axios | HTTP Client |
| JavaScript | jose | ID 토큰(RS256) 검증 |
| React | React Query | 서버 상태 관리 |
| iOS | Alamofire | HTTP Client |
| iOS | KeychainAccess | 토큰 보안 저장 |
| iOS | AuthenticationServices | 소셜 인가 화면(`ASWebAuthenticationSession`) |
| Android | Retrofit | HTTP Client |
| Android | OkHttp | HTTP Interceptor |
| Android | AndroidX Browser | 소셜 인가 화면(Custom Tabs) |
| Python | requests | HTTP Client |
| Python | PyJWT | ID 토큰 처리 |

---

## 참고

- [API Reference](API_REFERENCE.md) - API 엔드포인트 상세
- [Error Codes](ERROR_CODES.md) - 에러 코드 및 처리 가이드
- [시퀀스 다이어그램](SEQUENCE_DIAGRAMS.md) - 상세 API 흐름
