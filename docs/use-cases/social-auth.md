# 소셜 로그인 API 유즈케이스

## 개요

소셜 로그인(OAuth2) 및 계정 연동을 위한 API입니다.

지원하는 소셜 로그인 Provider:
- **Google** - OpenID Connect 지원
- **Naver** - OAuth 2.0
- **Kakao** - OAuth 2.0

## 유즈케이스 목록

| ID | 유즈케이스 | API Endpoint | 인증 필요 |
|---|---|---|---|
| UC-SOCIAL-001 | 소셜 로그인 URL 조회 | `GET /auth/oauth2/{provider}/authorize` | 아니오 |
| UC-SOCIAL-002 | 소셜 로그인 콜백 (리다이렉트) | `GET /auth/oauth2/{provider}/callback` | 아니오 |
| UC-SOCIAL-003 | 소셜 로그인 (코드 교환) | `POST /auth/oauth2/{provider}/login` | 아니오 |
| UC-SOCIAL-004 | 소셜 계정 연동 | `POST /auth/oauth2/{provider}/link` | 예 |
| UC-SOCIAL-005 | 소셜 계정 연동 해제 | `DELETE /auth/oauth2/{provider}/unlink` | 예 |
| UC-SOCIAL-006 | 연동된 소셜 계정 목록 조회 | `GET /auth/oauth2/accounts` | 예 |

---

## UC-SOCIAL-001: 소셜 로그인 URL 조회

### 설명
소셜 로그인을 위한 OAuth2 인증 URL을 생성하여 반환합니다.

### 선행조건
- 없음

### 기본 흐름
1. 클라이언트가 소셜 Provider를 지정하여 요청합니다.
2. 시스템이 CSRF 방지를 위한 state 값을 생성합니다.
3. 시스템이 해당 Provider의 인증 URL을 생성합니다.
4. 시스템이 인증 URL과 state 값을 반환합니다.

### 입력
- Path: `{provider}` - NAVER, KAKAO, GOOGLE 중 하나

### 출력
```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&state=...",
  "state": "random-uuid-state"
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| Google 인증 URL 요청 | provider=GOOGLE | 200 OK, Google OAuth URL 반환 |
| Naver 인증 URL 요청 | provider=NAVER | 200 OK, Naver OAuth URL 반환 |
| Kakao 인증 URL 요청 | provider=KAKAO | 200 OK, Kakao OAuth URL 반환 |
| 지원하지 않는 Provider | provider=FACEBOOK | 400 Bad Request |

---

## UC-SOCIAL-002: 소셜 로그인 콜백 (리다이렉트)

### 설명
OAuth2 인증 완료 후 Provider로부터의 콜백을 처리하고, 프론트엔드로 결과를 리다이렉트합니다.

### 선행조건
- UC-SOCIAL-001을 통해 인증 URL을 획득
- 사용자가 Provider에서 인증 완료

### 기본 흐름
1. Provider가 authorization code와 함께 콜백 URL을 호출합니다.
2. 시스템이 code를 토큰으로 교환합니다.
3. 시스템이 Provider API로 사용자 정보를 조회합니다.
4. 시스템이 기존 사용자를 찾거나 새 사용자를 생성합니다.
5. 시스템이 세션과 JWT 토큰을 생성합니다.
6. 시스템이 프론트엔드 success 페이지로 리다이렉트합니다.

### 대안 흐름
- **A1: 인증 실패** → 프론트엔드 error 페이지로 리다이렉트
- **A2: Provider API 오류** → 프론트엔드 error 페이지로 리다이렉트

### 입력
- Path: `{provider}` - NAVER, KAKAO, GOOGLE 중 하나
- Query: `code` - Authorization code
- Query: `state` - CSRF 방지 state 값
- Query: `error` (선택) - 에러 코드
- Query: `error_description` (선택) - 에러 설명

### 출력
- 성공: `/auth/social/success?accessToken=...&refreshToken=...&expiresIn=...`로 리다이렉트
- 실패: `/auth/social/error?error=...&description=...`로 리다이렉트

---

## UC-SOCIAL-003: 소셜 로그인 (코드 교환)

### 설명
클라이언트가 직접 authorization code를 전송하여 토큰을 발급받습니다.
SPA나 모바일 앱에서 사용합니다.

### 선행조건
- UC-SOCIAL-001을 통해 인증 URL을 획득
- 사용자가 Provider에서 인증 완료
- 클라이언트가 authorization code를 획득

### 기본 흐름
1. 클라이언트가 authorization code를 전송합니다.
2. 시스템이 code를 토큰으로 교환합니다.
3. 시스템이 Provider API로 사용자 정보를 조회합니다.
4. 시스템이 기존 사용자를 찾거나 새 사용자를 생성합니다.
5. 시스템이 세션과 JWT 토큰을 생성합니다.
6. 시스템이 토큰을 반환합니다.

### 입력
```json
{
  "code": "authorization_code_from_provider",
  "deviceType": "MOBILE",
  "deviceInfo": "iOS 17.0"
}
```

### 출력
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 신규 사용자 Google 로그인 | 유효한 Google code | 200 OK, 토큰 반환, 사용자 생성 |
| 기존 사용자 로그인 | 기존 사용자의 Provider code | 200 OK, 토큰 반환 |
| 잘못된 code | 만료/잘못된 code | 401 Unauthorized |
| 빈 code | "" | 400 Bad Request |

---

## UC-SOCIAL-004: 소셜 계정 연동

### 설명
기존 계정에 소셜 계정을 추가로 연동합니다.

### 선행조건
- 유효한 액세스 토큰 필요
- 해당 Provider로 이미 연동된 계정이 없어야 함

### 기본 흐름
1. 사용자가 authorization code를 전송합니다.
2. 시스템이 code를 토큰으로 교환합니다.
3. 시스템이 Provider API로 사용자 정보를 조회합니다.
4. 시스템이 해당 Provider ID가 다른 계정에 연동되어 있는지 확인합니다.
5. 시스템이 현재 사용자에게 소셜 계정을 연동합니다.
6. 시스템이 연동된 계정 정보를 반환합니다.

### 대안 흐름
- **A1: 이미 연동된 Provider** → 409 Conflict 반환
- **A2: 다른 계정에 연동된 소셜 ID** → 409 Conflict 반환

### 입력
```json
{
  "code": "authorization_code_from_provider"
}
```

### 출력
```json
{
  "provider": "GOOGLE",
  "providerId": "google-user-id-123",
  "email": "user@gmail.com",
  "name": "User Name",
  "connectedAt": "2024-01-15T10:30:00"
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 연동 | 유효한 code | 201 Created, SocialAccountDto 반환 |
| 이미 연동된 Provider | 같은 Provider 재연동 | 409 Conflict |
| 다른 계정에 연동된 소셜 ID | 이미 사용된 소셜 계정 | 409 Conflict |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |

---

## UC-SOCIAL-005: 소셜 계정 연동 해제

### 설명
연동된 소셜 계정을 해제합니다.

### 선행조건
- 유효한 액세스 토큰 필요
- 해당 Provider로 연동된 계정이 있어야 함
- 다른 로그인 수단(비밀번호 또는 다른 소셜)이 있어야 함

### 기본 흐름
1. 사용자가 연동 해제를 요청합니다.
2. 시스템이 해당 Provider 연동 정보를 찾습니다.
3. 시스템이 다른 로그인 수단이 있는지 확인합니다.
4. 시스템이 소셜 계정 연동을 삭제합니다.
5. 시스템이 204 No Content를 반환합니다.

### 대안 흐름
- **A1: 연동되지 않은 Provider** → 404 Not Found 반환
- **A2: 마지막 로그인 수단** → 400 Bad Request 반환

### 입력
- Header: `Authorization: Bearer {accessToken}`
- Path: `{provider}` - NAVER, KAKAO, GOOGLE 중 하나

### 출력
- 204 No Content (본문 없음)

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 연동 해제 | 연동된 Provider | 204 No Content |
| 연동되지 않은 Provider | 연동되지 않은 Provider | 404 Not Found |
| 마지막 로그인 수단 | 유일한 로그인 수단 | 400 Bad Request |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |

---

## UC-SOCIAL-006: 연동된 소셜 계정 목록 조회

### 설명
현재 사용자에게 연동된 모든 소셜 계정 목록을 조회합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 연동 계정 목록을 요청합니다.
2. 시스템이 사용자의 모든 소셜 계정 연동 정보를 조회합니다.
3. 시스템이 연동 계정 목록을 반환합니다.

### 입력
- Header: `Authorization: Bearer {accessToken}`

### 출력
```json
[
  {
    "provider": "GOOGLE",
    "providerId": "google-user-id-123",
    "email": "user@gmail.com",
    "name": "User Name",
    "connectedAt": "2024-01-15T10:30:00"
  },
  {
    "provider": "KAKAO",
    "providerId": "kakao-user-id-456",
    "email": "user@kakao.com",
    "name": "카카오이름",
    "connectedAt": "2024-01-20T14:00:00"
  }
]
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 연동 계정 있음 | 유효한 토큰 | 200 OK, 연동 계정 목록 |
| 연동 계정 없음 | 유효한 토큰 | 200 OK, 빈 배열 |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |

---

## 에러 코드

| HTTP Status | 에러 코드 | 설명 |
|---|---|---|
| 400 | BAD_REQUEST | 입력 데이터 유효성 검증 실패 |
| 400 | INVALID_PROVIDER | 지원하지 않는 Provider |
| 400 | CANNOT_UNLINK_LAST_LOGIN | 마지막 로그인 수단은 해제 불가 |
| 401 | INVALID_OAUTH_CODE | 유효하지 않은 authorization code |
| 401 | UNAUTHORIZED | 인증 필요 |
| 404 | SOCIAL_ACCOUNT_NOT_FOUND | 소셜 계정 연동 정보 없음 |
| 409 | ALREADY_LINKED | 이미 연동된 Provider |
| 409 | SOCIAL_ACCOUNT_IN_USE | 다른 계정에 연동된 소셜 ID |

---

## 시퀀스 다이어그램

### 소셜 로그인 흐름 (Authorization Code Flow)

```
User        Client            Backend              Provider
 |            |                   |                    |
 |-- 로그인 ---->|                   |                    |
 |            |-- GET /oauth2/{provider}/authorize --->|
 |            |<-- authorizationUrl + state ------------|
 |            |                   |                    |
 |<-- Redirect to Provider -------|                    |
 |---------------------------------------- 인증 ------->|
 |<-------------------------------------- code --------|
 |            |                   |                    |
 |-- code --->|                   |                    |
 |            |-- POST /oauth2/{provider}/login ------>|
 |            |                   |-- exchange code -->|
 |            |                   |<-- tokens ---------|
 |            |                   |-- get user info -->|
 |            |                   |<-- user info ------|
 |            |                   |                    |
 |            |                   |-- create/find user |
 |            |                   |-- create session   |
 |            |                   |-- generate JWT     |
 |            |<-- TokenResponseDto -------------------|
 |<-- tokens --|                   |                    |
```

### 소셜 계정 연동 흐름

```
User        Client            Backend              Provider
 |            |                   |                    |
 |-- 연동 요청 ->|                   |                    |
 |            |-- GET /oauth2/{provider}/authorize --->|
 |            |<-- authorizationUrl + state ------------|
 |<-- Redirect to Provider -------|                    |
 |---------------------------------------- 인증 ------->|
 |<-------------------------------------- code --------|
 |-- code --->|                   |                    |
 |            |-- POST /oauth2/{provider}/link ------->|
 |            |    (with accessToken)                  |
 |            |                   |-- exchange code -->|
 |            |                   |<-- tokens ---------|
 |            |                   |-- get user info -->|
 |            |                   |<-- user info ------|
 |            |                   |                    |
 |            |                   |-- check duplicate  |
 |            |                   |-- link account     |
 |            |<-- SocialAccountDto -------------------|
 |<-- 연동 완료 |                   |                    |
```
