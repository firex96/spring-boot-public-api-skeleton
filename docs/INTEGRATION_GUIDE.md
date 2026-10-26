# Integration Guide

> Public API Skeleton 클라이언트 연동 가이드

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
| Base URL (개발) | `https://api-dev.example.com` |
| Base URL (스테이징) | `https://api-staging.example.com` |
| Base URL (운영) | `https://api.example.com` |
| Content-Type | `application/json` |
| 인증 방식 | Bearer Token (JWT) |

### 필수 헤더

```http
Content-Type: application/json
Authorization: Bearer {access_token}
```

### 응답 형식

**성공 응답:** 래퍼 없이 각 API 의 응답 모델을 그대로 돌려줍니다. 예: 로그인
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

**에러 응답:** `ErrorResponseModel` 입니다(`docs/ERROR_CODES.md`). `reason` 은 사용자에게 그대로 보여도 되는 문장입니다.
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

---

## Web Application 연동

### React/Next.js 연동

#### 1. API Client 설정

```typescript
// lib/api-client.ts
import axios, { AxiosInstance, AxiosError } from 'axios';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL;

class ApiClient {
  private client: AxiosInstance;
  private isRefreshing = false;
  private refreshSubscribers: ((token: string) => void)[] = [];

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors() {
    // Request interceptor - 토큰 자동 추가
    this.client.interceptors.request.use((config) => {
      const token = this.getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });

    // Response interceptor - 토큰 갱신 처리
    this.client.interceptors.response.use(
      (response) => response,
      async (error: AxiosError) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && originalRequest) {
          if (!this.isRefreshing) {
            this.isRefreshing = true;

            try {
              const newToken = await this.refreshToken();
              this.refreshSubscribers.forEach((callback) => callback(newToken));
              this.refreshSubscribers = [];
              return this.client(originalRequest);
            } catch (refreshError) {
              this.logout();
              throw refreshError;
            } finally {
              this.isRefreshing = false;
            }
          }

          return new Promise((resolve) => {
            this.refreshSubscribers.push((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(this.client(originalRequest));
            });
          });
        }

        throw error;
      }
    );
  }

  private getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  private getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token');
  }

  private setTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  }

  private async refreshToken(): Promise<string> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token');
    }

    const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
      refreshToken,
    });

    const { accessToken, refreshToken: newRefreshToken } = response.data;
    this.setTokens(accessToken, newRefreshToken);
    return accessToken;
  }

  logout() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    window.location.href = '/login';
  }

  // API Methods
  async get<T>(url: string, params?: object): Promise<T> {
    const response = await this.client.get(url, { params });
    return response.data;
  }

  async post<T>(url: string, data?: object): Promise<T> {
    const response = await this.client.post(url, data);
    return response.data;
  }

  async put<T>(url: string, data?: object): Promise<T> {
    const response = await this.client.put(url, data);
    return response.data;
  }

  async delete<T>(url: string): Promise<T> {
    const response = await this.client.delete(url);
    return response.data;
  }
}

export const apiClient = new ApiClient();
```

#### 2. Auth Service 구현

```typescript
// services/auth-service.ts
import { apiClient } from '@/lib/api-client';

interface LoginRequest {
  loginId: string;
  password: string;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

interface UserResponse {
  userId: number;
  loginId: string;
  nickname: string;
  email: string;
}

export const authService = {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await apiClient.post<LoginResponse>(
      '/api/v1/auth/login',
      credentials
    );

    // 토큰 저장
    localStorage.setItem('access_token', response.accessToken);
    localStorage.setItem('refresh_token', response.refreshToken);

    return response;
  },

  async logout(): Promise<void> {
    try {
      await apiClient.post('/api/v1/auth/logout');
    } finally {
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
    }
  },

  async getCurrentUser(): Promise<UserResponse> {
    return apiClient.get<UserResponse>('/api/v1/users/me');
  },

  async signup(data: {
    loginId: string;
    password: string;
    nickname: string;
    email: string;
  }): Promise<void> {
    await apiClient.post('/api/v1/auth/signup', data);
  },

  isAuthenticated(): boolean {
    return !!localStorage.getItem('access_token');
  },
};
```

#### 3. Auth Context (React)

```tsx
// contexts/AuthContext.tsx
import React, { createContext, useContext, useEffect, useState } from 'react';
import { authService } from '@/services/auth-service';

interface User {
  userId: number;
  loginId: string;
  nickname: string;
  email: string;
}

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (loginId: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initAuth = async () => {
      if (authService.isAuthenticated()) {
        try {
          setUser(await authService.getCurrentUser());
        } catch (error) {
          // 토큰 만료 등으로 실패 시 로그아웃
          localStorage.removeItem('access_token');
          localStorage.removeItem('refresh_token');
        }
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const login = async (loginId: string, password: string) => {
    await authService.login({ loginId, password });
    setUser(await authService.getCurrentUser());
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
        logout,
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

#### 4. Protected Route

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

#### 1. Composable 설정

```typescript
// composables/useAuth.ts
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL;

interface User {
  userId: number;
  loginId: string;
  nickname: string;
  email: string;
}

const user = ref<User | null>(null);
const isLoading = ref(true);

export function useAuth() {
  const router = useRouter();

  const isAuthenticated = computed(() => !!user.value);

  const getAccessToken = () => localStorage.getItem('access_token');
  const getRefreshToken = () => localStorage.getItem('refresh_token');

  const setTokens = (accessToken: string, refreshToken: string) => {
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  };

  const clearTokens = () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
  };

  const login = async (loginId: string, password: string) => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/auth/login`, {
      loginId,
      password,
    });

    const { accessToken, refreshToken } = response.data;
    setTokens(accessToken, refreshToken);

    await fetchUser();
  };

  const logout = async () => {
    try {
      await axios.post(
        `${API_BASE_URL}/api/v1/auth/logout`,
        {},
        {
          headers: { Authorization: `Bearer ${getAccessToken()}` },
        }
      );
    } finally {
      clearTokens();
      user.value = null;
      router.push('/login');
    }
  };

  const fetchUser = async () => {
    const token = getAccessToken();
    if (!token) {
      isLoading.value = false;
      return;
    }

    try {
      const response = await axios.get(`${API_BASE_URL}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      user.value = response.data;
    } catch (error) {
      clearTokens();
    } finally {
      isLoading.value = false;
    }
  };

  return {
    user,
    isLoading,
    isAuthenticated,
    login,
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

class APIClient {
    static let shared = APIClient()

    private let baseURL = "https://api.example.com"
    private let session = URLSession.shared

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

    // MARK: - Request Methods

    func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: Encodable? = nil,
        requiresAuth: Bool = true
    ) async throws -> T {
        var urlRequest = URLRequest(url: URL(string: "\(baseURL)\(endpoint)")!)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")

        if requiresAuth, let token = accessToken {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = body {
            urlRequest.httpBody = try JSONEncoder().encode(body)
        }

        let (data, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        // 401 처리 - 토큰 갱신 시도
        if httpResponse.statusCode == 401 && requiresAuth {
            try await refreshAccessToken()
            return try await request(endpoint: endpoint, method: method, body: body)
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let errorResponse = try? JSONDecoder().decode(ErrorResponse.self, from: data)
            throw APIError.serverError(
                code: errorResponse?.divisionCode ?? "UNKNOWN",
                message: errorResponse?.reason ?? errorResponse?.resultMessage ?? "Unknown error"
            )
        }

        return try JSONDecoder().decode(T.self, from: data)
    }

    // MARK: - Token Refresh

    private func refreshAccessToken() async throws {
        guard let refreshToken = refreshToken else {
            throw APIError.notAuthenticated
        }

        struct RefreshRequest: Encodable {
            let refreshToken: String
        }

        struct RefreshResponse: Decodable {
            let accessToken: String
            let refreshToken: String
        }

        let response: RefreshResponse = try await request(
            endpoint: "/api/v1/auth/refresh",
            method: "POST",
            body: RefreshRequest(refreshToken: refreshToken),
            requiresAuth: false
        )

        self.accessToken = response.accessToken
        self.refreshToken = response.refreshToken
    }

    // MARK: - Auth Methods

    func login(loginId: String, password: String) async throws -> User {
        struct LoginRequest: Encodable {
            let loginId: String
            let password: String
        }

        struct LoginResponse: Decodable {
            let accessToken: String
            let refreshToken: String
        }

        let response: LoginResponse = try await request(
            endpoint: "/api/v1/auth/login",
            method: "POST",
            body: LoginRequest(loginId: loginId, password: password),
            requiresAuth: false
        )

        accessToken = response.accessToken
        refreshToken = response.refreshToken

        return try await getCurrentUser()
    }

    func logout() async throws {
        try await request(
            endpoint: "/api/v1/auth/logout",
            method: "POST"
        ) as EmptyResponse

        accessToken = nil
        refreshToken = nil
    }

    func getCurrentUser() async throws -> User {
        return try await request(endpoint: "/api/v1/users/me")
    }
}

// MARK: - Models

struct User: Decodable {
    let userId: Int
    let loginId: String
    let nickname: String
    let email: String
}

struct ErrorResponse: Decodable {
    let status: Int
    let divisionCode: String
    let reason: String?
    let resultMessage: String
}

struct EmptyResponse: Decodable {}

enum APIError: Error {
    case invalidResponse
    case notAuthenticated
    case serverError(code: String, message: String)
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

```kotlin
// ApiService.kt
interface ApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<TokenResponse>

    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<UserResponse>
}

// Models
data class LoginRequest(
    val loginId: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class UserResponse(
    val userId: Int,
    val loginId: String,
    val nickname: String,
    val email: String
)
```

#### 2. Token Interceptor

```kotlin
// AuthInterceptor.kt
class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()

        // 인증이 필요없는 요청
        if (originalRequest.header("No-Auth") != null) {
            return chain.proceed(
                originalRequest.newBuilder()
                    .removeHeader("No-Auth")
                    .build()
            )
        }

        val accessToken = tokenManager.getAccessToken()

        val request = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return chain.proceed(request)
    }
}

// TokenAuthenticator.kt
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) : Authenticator {

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        // 이미 재시도한 경우
        if (response.request.header("Retry-Auth") != null) {
            return null
        }

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return runBlocking {
            try {
                val refreshResponse = authApi.refreshToken(
                    RefreshRequest(refreshToken)
                )

                if (refreshResponse.isSuccessful) {
                    val tokens = refreshResponse.body()
                    if (tokens != null) {
                        tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)

                        return@runBlocking response.request.newBuilder()
                            .header("Authorization", "Bearer ${tokens.accessToken}")
                            .header("Retry-Auth", "true")
                            .build()
                    }
                }

                // 갱신 실패 - 로그아웃
                tokenManager.clearTokens()
                null
            } catch (e: Exception) {
                tokenManager.clearTokens()
                null
            }
        }
    }
}
```

#### 3. API Client 설정

```kotlin
// ApiClient.kt
object ApiClient {
    private const val BASE_URL = "https://api.example.com/"

    fun create(context: Context): ApiService {
        val tokenManager = TokenManager(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Auth API (토큰 갱신용)
        val authClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val authApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)

        // Main API Client
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(tokenManager, authApi))
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// TokenManager.kt
class TokenManager(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun clearTokens() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }
}
```

---

## Third-Party Service 연동

### Server-to-Server 연동

서버 간 통신에는 **Client Credentials Flow**를 사용합니다.

#### 1. 클라이언트 등록 요청

관리자에게 다음 정보와 함께 클라이언트 등록을 요청합니다:

- 서비스 이름
- 필요한 권한 (scope)
- 콜백 URL (필요 시)

등록 후 `client_id`와 `client_secret`을 발급받습니다.

#### 2. Access Token 발급

```bash
curl -X POST https://api.example.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=your-client-id" \
  -d "client_secret=your-client-secret" \
  -d "scope=read:users"
```

**응답:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read:users"
}
```

#### 3. API 호출

```bash
curl https://api.example.com/api/v1/admin/users \
  -H "Authorization: Bearer {access_token}"
```

#### 4. Python 예시

```python
import requests
from datetime import datetime, timedelta

class ApiClient:
    def __init__(self, base_url: str, client_id: str, client_secret: str):
        self.base_url = base_url
        self.client_id = client_id
        self.client_secret = client_secret
        self.access_token = None
        self.token_expires_at = None

    def _get_token(self) -> str:
        """토큰 발급 또는 캐시된 토큰 반환"""
        if self.access_token and self.token_expires_at > datetime.now():
            return self.access_token

        response = requests.post(
            f"{self.base_url}/oauth2/token",
            data={
                "grant_type": "client_credentials",
                "client_id": self.client_id,
                "client_secret": self.client_secret,
            }
        )
        response.raise_for_status()

        data = response.json()
        self.access_token = data["access_token"]
        self.token_expires_at = datetime.now() + timedelta(
            seconds=data["expires_in"] - 60  # 1분 여유
        )

        return self.access_token

    def request(self, method: str, endpoint: str, **kwargs) -> dict:
        """API 요청"""
        headers = kwargs.pop("headers", {})
        headers["Authorization"] = f"Bearer {self._get_token()}"

        response = requests.request(
            method,
            f"{self.base_url}{endpoint}",
            headers=headers,
            **kwargs
        )
        response.raise_for_status()
        return response.json()

    def get_users(self, page: int = 0, size: int = 20) -> dict:
        return self.request("GET", "/api/v1/admin/users", params={
            "page": page,
            "size": size
        })

# 사용 예시
client = ApiClient(
    base_url="https://api.example.com",
    client_id="your-client-id",
    client_secret="your-client-secret"
)

users = client.get_users()
print(users)
```

---

## OAuth2 Client 연동

사용자 인증이 필요한 Third-Party 앱은 **Authorization Code Flow**를 사용합니다.

### 연동 흐름

```
1. 클라이언트 등록 → client_id, client_secret 발급
2. 사용자를 Authorization Endpoint로 리다이렉트
3. 사용자 로그인 및 동의
4. Authorization Code 수신
5. Authorization Code로 Access Token 교환
6. Access Token으로 API 호출
```

### 1. Authorization URL 생성

```typescript
const authorizationUrl = new URL('https://api.example.com/oauth2/authorize');
authorizationUrl.searchParams.set('response_type', 'code');
authorizationUrl.searchParams.set('client_id', CLIENT_ID);
authorizationUrl.searchParams.set('redirect_uri', 'https://your-app.com/callback');
authorizationUrl.searchParams.set('scope', 'openid profile email');
authorizationUrl.searchParams.set('state', generateRandomState());

// PKCE (권장)
const codeVerifier = generateCodeVerifier();
const codeChallenge = await generateCodeChallenge(codeVerifier);
authorizationUrl.searchParams.set('code_challenge', codeChallenge);
authorizationUrl.searchParams.set('code_challenge_method', 'S256');

// 사용자를 Authorization URL로 리다이렉트
window.location.href = authorizationUrl.toString();
```

### 2. Callback 처리

```typescript
// /callback 페이지
const urlParams = new URLSearchParams(window.location.search);
const code = urlParams.get('code');
const state = urlParams.get('state');

// State 검증
if (state !== savedState) {
  throw new Error('Invalid state');
}

// Token 교환
const tokenResponse = await fetch('https://api.example.com/oauth2/token', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    grant_type: 'authorization_code',
    code: code,
    redirect_uri: 'https://your-app.com/callback',
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    code_verifier: codeVerifier, // PKCE 사용 시
  }),
});

const tokens = await tokenResponse.json();
// {
//   "access_token": "...",
//   "refresh_token": "...",
//   "id_token": "...",  // openid scope 요청 시
//   "expires_in": 3600,
//   "token_type": "Bearer"
// }
```

### 3. PKCE 구현

```typescript
// PKCE Helper Functions
function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(hash));
}

function base64UrlEncode(buffer: Uint8Array): string {
  return btoa(String.fromCharCode(...buffer))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
```

---

## 소셜 로그인 연동

### Web Application 소셜 로그인

#### 1. 소셜 로그인 버튼 구현

```tsx
// components/SocialLogin.tsx
import { useState } from 'react';

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
    // 현재 페이지 URL을 state로 저장 (로그인 후 복귀용)
    const returnUrl = window.location.href;
    sessionStorage.setItem('social_login_return_url', returnUrl);

    // 소셜 로그인 페이지로 이동
    window.location.href = `${API_BASE_URL}/api/v1/social/login/${provider}`;
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

```tsx
// pages/social/callback.tsx
import { useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';

export default function SocialCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setTokens, fetchUser } = useAuth();

  useEffect(() => {
    const handleCallback = async () => {
      const accessToken = searchParams.get('access_token');
      const refreshToken = searchParams.get('refresh_token');
      const error = searchParams.get('error');

      if (error) {
        alert(`로그인 실패: ${error}`);
        router.push('/login');
        return;
      }

      if (accessToken && refreshToken) {
        // 토큰 저장
        localStorage.setItem('access_token', accessToken);
        localStorage.setItem('refresh_token', refreshToken);

        // 사용자 정보 조회
        await fetchUser();

        // 원래 페이지로 복귀
        const returnUrl = sessionStorage.getItem('social_login_return_url') || '/';
        sessionStorage.removeItem('social_login_return_url');
        router.push(returnUrl);
      }
    };

    handleCallback();
  }, [searchParams, router, fetchUser]);

  return <div>로그인 처리 중...</div>;
}
```

### Mobile 소셜 로그인 (네이티브)

모바일 앱에서는 네이티브 SDK를 사용하여 소셜 로그인 후, 받은 토큰을 서버에 전달합니다.

#### iOS (Swift) - Google 로그인

```swift
import GoogleSignIn

class SocialLoginManager {
    func signInWithGoogle(presenting: UIViewController) async throws -> User {
        let result = try await GIDSignIn.sharedInstance.signIn(
            withPresenting: presenting
        )

        guard let idToken = result.user.idToken?.tokenString else {
            throw SocialLoginError.noIdToken
        }

        // 서버에 ID Token 전달
        return try await APIClient.shared.socialLogin(
            provider: "google",
            idToken: idToken
        )
    }
}

// APIClient extension
extension APIClient {
    func socialLogin(provider: String, idToken: String) async throws -> User {
        struct SocialLoginRequest: Encodable {
            let idToken: String
        }

        struct LoginResponse: Decodable {
            let accessToken: String
            let refreshToken: String
        }

        let response: LoginResponse = try await request(
            endpoint: "/api/v1/social/login/\(provider)/token",
            method: "POST",
            body: SocialLoginRequest(idToken: idToken),
            requiresAuth: false
        )

        // 토큰 저장
        accessToken = response.accessToken
        refreshToken = response.refreshToken

        return try await getCurrentUser()
    }
}
```

#### Android (Kotlin) - Kakao 로그인

```kotlin
// KakaoLoginManager.kt
class KakaoLoginManager(
    private val context: Context,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(): Result<User> {
        return try {
            // 카카오톡 설치 여부에 따라 로그인 방식 선택
            val token = if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                loginWithKakaoTalk()
            } else {
                loginWithKakaoAccount()
            }

            // 서버에 Access Token 전달
            val response = apiService.socialLoginWithToken(
                provider = "kakao",
                request = SocialTokenRequest(accessToken = token.accessToken)
            )

            val tokens = response.body()
            if (response.isSuccessful && tokens != null) {
                tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)

                // 사용자 정보 조회
                val userResponse = apiService.getCurrentUser()
                val user = userResponse.body() ?: return Result.failure(Exception("User lookup failed"))
                Result.success(user)
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loginWithKakaoTalk(): OAuthToken {
        return suspendCoroutine { continuation ->
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    continuation.resumeWithException(error)
                } else if (token != null) {
                    continuation.resume(token)
                }
            }
        }
    }

    private suspend fun loginWithKakaoAccount(): OAuthToken {
        return suspendCoroutine { continuation ->
            UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
                if (error != null) {
                    continuation.resumeWithException(error)
                } else if (token != null) {
                    continuation.resume(token)
                }
            }
        }
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
| JavaScript | jose | JWT 검증 |
| React | React Query | 서버 상태 관리 |
| iOS | Alamofire | HTTP Client |
| iOS | KeychainAccess | 토큰 보안 저장 |
| Android | Retrofit | HTTP Client |
| Android | OkHttp | HTTP Interceptor |
| Python | requests | HTTP Client |
| Python | PyJWT | JWT 처리 |

---

## 참고

- [API Reference](API_REFERENCE.md) - API 엔드포인트 상세
- [Error Codes](ERROR_CODES.md) - 에러 코드 및 처리 가이드
- [시퀀스 다이어그램](SEQUENCE_DIAGRAMS.md) - 상세 API 흐름
