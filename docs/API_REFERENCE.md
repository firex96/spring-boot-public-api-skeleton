# API Reference

> Public API Skeleton의 모든 API 엔드포인트 상세 명세

## 목차

- [공통 사항](#공통-사항)
- [인증 API](#인증-api)
- [소셜 로그인 API](#소셜-로그인-api)
- [사용자 API](#사용자-api)
- [사용자 셀프 서비스 API](#사용자-셀프-서비스-api)
- [OAuth2 Authorization Server API](#oauth2-authorization-server-api)
- [OpenID Connect API](#openid-connect-api)
- [관리자 API](#관리자-api)

---

## 공통 사항

### Base URL

```
개발: https://dev-api.example.com
스테이징: https://staging-api.example.com
프로덕션: https://api.example.com
```

### 인증 헤더

인증이 필요한 API는 `Authorization` 헤더에 Bearer 토큰을 포함해야 합니다.

```
Authorization: Bearer {access_token}
```

### 공통 응답 형식

#### 성공 응답

```json
{
  "data": { ... }
}
```

#### 에러 응답

```json
{
  "status": 400,
  "divisionCode": "G001",
  "resultMsg": "Bad Request Exception"
}
```

### 페이지네이션

페이지네이션이 적용된 API는 다음 쿼리 파라미터를 지원합니다:

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `page` | int | 0 | 페이지 번호 (0부터 시작) |
| `size` | int | 20 | 페이지 크기 |
| `sort` | string | - | 정렬 기준 (예: `createdAt,desc`) |

응답 형식:

```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 100,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

---

## 인증 API

### POST /auth/signup

회원가입

#### Request

```json
{
  "loginId": "user@example.com",
  "password": "SecureP@ss123",
  "email": "user@example.com",
  "name": "홍길동",
  "phone": "010-1234-5678"
}
```

#### Response `201 Created`

```json
{
  "id": 12345,
  "loginId": "user@example.com",
  "email": "user@example.com",
  "name": "홍길동",
  "status": "ACTIVE",
  "createdAt": "2024-01-15T10:30:00"
}
```

#### Errors

| 코드 | 설명 |
|------|------|
| 400 | 유효하지 않은 요청 (비밀번호 규칙 미충족 등) |
| 409 | 이미 존재하는 loginId |

---

### POST /auth/login

로그인

#### Request

```json
{
  "loginId": "user@example.com",
  "password": "SecureP@ss123"
}
```

#### Response `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### Errors

| 코드 | 설명 |
|------|------|
| 401 | 인증 실패 (잘못된 아이디/비밀번호) |
| 423 | 계정 잠금 (로그인 시도 초과) |

#### cURL 예시

```bash
curl -X POST https://api.example.com/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginId": "user@example.com",
    "password": "SecureP@ss123"
  }'
```

---

### POST /auth/logout

로그아웃 (현재 세션 무효화)

**인증 필요**

#### Response `204 No Content`

(응답 본문 없음)

#### cURL 예시

```bash
curl -X POST https://api.example.com/auth/logout \
  -H "Authorization: Bearer {access_token}"
```

---

### POST /auth/refresh

토큰 갱신

#### Request

```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Response `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### Errors

| 코드 | 설명 |
|------|------|
| 401 | 유효하지 않거나 만료된 Refresh Token |

---

### POST /auth/password/change

비밀번호 변경

**인증 필요**

#### Request

```json
{
  "currentPassword": "OldP@ss123",
  "newPassword": "NewP@ss456"
}
```

#### Response `204 No Content`

(응답 본문 없음)

#### Errors

| 코드 | 설명 |
|------|------|
| 400 | 현재 비밀번호 불일치 또는 새 비밀번호 규칙 미충족 |

---

## 소셜 로그인 API

### GET /auth/oauth2/{provider}/authorize

소셜 로그인 URL 조회

#### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| provider | string | 소셜 제공자 (`GOOGLE`, `KAKAO`, `NAVER`) |

#### Response `200 OK`

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "random-state-string"
}
```

---

### GET /auth/oauth2/{provider}/callback

소셜 로그인 콜백 (리다이렉트)

OAuth Provider로부터의 콜백을 처리합니다. 성공 시 프론트엔드로 토큰과 함께 리다이렉트됩니다.

#### Query Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| code | string | Authorization Code |
| state | string | CSRF 방지용 state |

#### Response `302 Redirect`

성공: `/auth/social/success?accessToken=...&refreshToken=...&expiresIn=...`
실패: `/auth/social/error?error=...&description=...`

---

### POST /auth/oauth2/{provider}/login

소셜 로그인 (코드 교환)

#### Request

```json
{
  "code": "authorization_code_from_provider",
  "deviceType": "WEB",
  "deviceInfo": "Mozilla/5.0..."
}
```

#### Response `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

---

### POST /auth/oauth2/{provider}/link

소셜 계정 연동

**인증 필요**

기존 계정에 소셜 계정을 연동합니다.

#### Request

```json
{
  "code": "authorization_code_from_provider"
}
```

#### Response `201 Created`

```json
{
  "provider": "GOOGLE",
  "email": "user@gmail.com",
  "name": "홍길동",
  "linkedAt": "2024-01-15T10:30:00"
}
```

---

### DELETE /auth/oauth2/{provider}/unlink

소셜 계정 연동 해제

**인증 필요**

#### Response `204 No Content`

(응답 본문 없음)

#### Errors

| 코드 | 설명 |
|------|------|
| 400 | 마지막 로그인 수단을 해제하려는 경우 |

---

### GET /auth/oauth2/accounts

연동된 소셜 계정 목록 조회

**인증 필요**

#### Response `200 OK`

```json
[
  {
    "provider": "GOOGLE",
    "email": "user@gmail.com",
    "name": "홍길동",
    "linkedAt": "2024-01-15T10:30:00"
  },
  {
    "provider": "KAKAO",
    "email": "user@kakao.com",
    "name": "홍길동",
    "linkedAt": "2024-01-16T15:00:00"
  }
]
```

---

## 사용자 API

### GET /users/me

내 정보 조회

**인증 필요**

#### Response `200 OK`

```json
{
  "id": 12345,
  "loginId": "user@example.com",
  "email": "user@example.com",
  "name": "홍길동",
  "nickname": "길동이",
  "profileImage": "https://cdn.example.com/profiles/12345.jpg",
  "status": "ACTIVE",
  "lastLoginAt": "2024-01-15T10:30:00",
  "createdAt": "2024-01-01T00:00:00"
}
```

---

### DELETE /users/me/sessions

전체 세션 로그아웃 (현재 세션 제외)

**인증 필요**

#### Response `204 No Content`

(응답 본문 없음)

---

### DELETE /users/me/sessions/{sessionId}

특정 세션 로그아웃

**인증 필요**

#### Response `204 No Content`

(응답 본문 없음)

---

## 사용자 셀프 서비스 API

### GET /user/profile

내 프로필 조회

**인증 필요**

#### Response `200 OK`

```json
{
  "id": 12345,
  "name": "홍길동",
  "nickname": "길동이",
  "email": "user@example.com",
  "phone": "010-1234-5678",
  "profileImage": "https://cdn.example.com/profiles/12345.jpg"
}
```

---

### PUT /user/profile

내 프로필 수정

**인증 필요**

#### Request

```json
{
  "nickname": "새닉네임",
  "phone": "010-9876-5432"
}
```

#### Response `200 OK`

```json
{
  "id": 12345,
  "name": "홍길동",
  "nickname": "새닉네임",
  "email": "user@example.com",
  "phone": "010-9876-5432",
  "profileImage": "https://cdn.example.com/profiles/12345.jpg"
}
```

---

### POST /user/password/reset-request

비밀번호 재설정 요청

이메일로 비밀번호 재설정 링크를 발송합니다.

#### Request

```json
{
  "email": "user@example.com"
}
```

#### Response `200 OK`

```json
{
  "message": "이메일이 등록되어 있다면 비밀번호 재설정 링크가 발송됩니다"
}
```

---

### POST /user/password/reset

비밀번호 재설정 완료

#### Request

```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewP@ss123"
}
```

#### Response `200 OK`

```json
{
  "success": true,
  "message": "비밀번호가 변경되었습니다"
}
```

---

### POST /user/password/change

비밀번호 변경

**인증 필요**

#### Request

```json
{
  "currentPassword": "OldP@ss123",
  "newPassword": "NewP@ss456"
}
```

#### Response `200 OK`

```json
{
  "success": true,
  "message": "비밀번호가 변경되었습니다"
}
```

---

### POST /user/email/verify-request

이메일 인증 요청

**인증 필요**

#### Request

```json
{
  "email": "new-email@example.com"
}
```

#### Response `200 OK`

```json
{
  "message": "인증 이메일이 발송되었습니다"
}
```

---

### POST /user/email/verify

이메일 인증 완료

#### Request

```json
{
  "token": "verification-token-from-email"
}
```

#### Response `200 OK`

```json
{
  "success": true,
  "message": "이메일이 인증되었습니다"
}
```

---

### GET /user/sessions

내 세션 목록 조회

**인증 필요**

#### Response `200 OK`

```json
[
  {
    "id": 1,
    "deviceType": "WEB",
    "deviceInfo": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...",
    "ipAddress": "192.168.1.100",
    "lastActivityAt": "2024-01-15T10:30:00",
    "isCurrent": true
  },
  {
    "id": 2,
    "deviceType": "MOBILE_IOS",
    "deviceInfo": "iPhone 15 Pro",
    "ipAddress": "192.168.1.101",
    "lastActivityAt": "2024-01-14T18:00:00",
    "isCurrent": false
  }
]
```

---

### DELETE /user/sessions/{sessionId}

특정 세션 종료

**인증 필요**

#### Response `200 OK`

```json
{
  "success": true,
  "message": "세션이 종료되었습니다"
}
```

---

### DELETE /user/sessions

다른 모든 세션 종료

**인증 필요**

현재 세션을 제외한 모든 세션을 종료합니다.

#### Response `200 OK`

```json
{
  "success": true,
  "message": "3개의 세션이 종료되었습니다"
}
```

---

## OAuth2 Authorization Server API

### GET /oauth2/authorize

Authorization 요청 (Authorization Code Flow 시작)

#### Query Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| response_type | Y | `code` |
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope | N | 요청 범위 (space-separated) |
| state | N | CSRF 방지용 |
| code_challenge | N | PKCE code challenge |
| code_challenge_method | N | `plain` 또는 `S256` |
| nonce | N | ID Token replay 방지용 |

#### Response

인증되지 않은 경우: 로그인 페이지로 리다이렉트
인증된 경우 (1st party): `redirect_uri?code=xxx&state=yyy`로 리다이렉트
인증된 경우 (3rd party): 동의 페이지로 리다이렉트

---

### GET /oauth2/consent

동의 정보 조회

**인증 필요**

#### Query Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope | N | 요청 범위 |
| state | N | CSRF 방지용 |

#### Response `200 OK`

```json
{
  "clientId": "my-app",
  "clientName": "My Application",
  "requestedScopes": ["openid", "profile", "email"],
  "scopeDescriptions": {
    "openid": "OpenID Connect 인증",
    "profile": "프로필 정보 접근",
    "email": "이메일 주소 접근"
  }
}
```

---

### POST /oauth2/consent

동의 처리

**인증 필요**

#### Query Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope | N | 요청 범위 |
| state | N | CSRF 방지용 |
| approve | Y | 동의 여부 (`true`/`false`) |

#### Response `302 Redirect`

동의: `redirect_uri?code=xxx&state=yyy`
거부: `redirect_uri?error=access_denied&error_description=...&state=yyy`

---

### POST /oauth2/token

토큰 발급

**Content-Type: application/x-www-form-urlencoded**

#### Authorization Code Grant

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| grant_type | Y | `authorization_code` |
| code | Y | Authorization Code |
| redirect_uri | Y | 리다이렉트 URI |
| client_id | Y | 클라이언트 ID |
| client_secret | N | 클라이언트 시크릿 (Confidential Client) |
| code_verifier | N | PKCE code verifier |

#### Refresh Token Grant

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| grant_type | Y | `refresh_token` |
| refresh_token | Y | Refresh Token |
| client_id | Y | 클라이언트 ID |
| client_secret | N | 클라이언트 시크릿 |
| scope | N | 요청 범위 (축소만 가능) |

#### Client Credentials Grant

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| grant_type | Y | `client_credentials` |
| client_id | Y | 클라이언트 ID |
| client_secret | Y | 클라이언트 시크릿 |
| scope | N | 요청 범위 |

#### Response `200 OK`

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid profile email"
}
```

#### cURL 예시

```bash
curl -X POST https://api.example.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=https://my-app.com/callback" \
  -d "client_id=my-client-id" \
  -d "client_secret=my-client-secret"
```

---

### POST /oauth2/introspect

토큰 검증

**Content-Type: application/x-www-form-urlencoded**

#### Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| token | Y | 검증할 토큰 |
| token_type_hint | N | `access_token` 또는 `refresh_token` |
| client_id | Y | 클라이언트 ID |
| client_secret | N | 클라이언트 시크릿 |

#### Response `200 OK`

```json
{
  "active": true,
  "sub": "12345",
  "client_id": "my-client-id",
  "scope": "openid profile email",
  "exp": 1705312200,
  "iat": 1705308600,
  "iss": "https://api.example.com"
}
```

---

### POST /oauth2/revoke

토큰 취소

**Content-Type: application/x-www-form-urlencoded**

#### Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| token | Y | 취소할 토큰 |
| token_type_hint | N | `access_token` 또는 `refresh_token` |
| client_id | Y | 클라이언트 ID |
| client_secret | N | 클라이언트 시크릿 |

#### Response `200 OK`

(응답 본문 없음)

---

## OpenID Connect API

### GET /.well-known/openid-configuration

OpenID Connect Discovery

#### Response `200 OK`

```json
{
  "issuer": "https://api.example.com",
  "authorization_endpoint": "https://api.example.com/oauth2/authorize",
  "token_endpoint": "https://api.example.com/oauth2/token",
  "userinfo_endpoint": "https://api.example.com/oauth2/userinfo",
  "jwks_uri": "https://api.example.com/.well-known/jwks.json",
  "revocation_endpoint": "https://api.example.com/oauth2/revoke",
  "introspection_endpoint": "https://api.example.com/oauth2/introspect"
}
```

---

### GET /.well-known/jwks.json

JSON Web Key Set

ID Token 검증을 위한 공개 키 정보를 반환합니다.

#### Response `200 OK`

```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e": "AQAB",
      "kid": "key-id-1",
      "alg": "RS256",
      "use": "sig"
    }
  ]
}
```

---

### GET /oauth2/userinfo

사용자 정보 조회

**인증 필요**

#### Response `200 OK`

```json
{
  "sub": "12345",
  "name": "홍길동",
  "nickname": "길동이",
  "picture": "https://cdn.example.com/profiles/12345.jpg",
  "email": "user@example.com",
  "email_verified": true,
  "phone_number": "010-1234-5678",
  "phone_number_verified": false,
  "updated_at": 1705312200
}
```

---

## 관리자 API

### 감사 로그 API

#### GET /admin/audit/logs

감사 로그 목록 조회

**권한 필요**: `admin:audit:read`

#### Query Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| page | int | 페이지 번호 |
| size | int | 페이지 크기 (기본: 20) |
| sort | string | 정렬 (기본: `createdAt,desc`) |

#### Response `200 OK`

```json
{
  "content": [
    {
      "id": 1,
      "eventType": "LOGIN_SUCCESS",
      "eventCategory": "AUTH",
      "userId": 12345,
      "username": "user@example.com",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "resourceType": "SESSION",
      "resourceId": "session-123",
      "details": { "deviceType": "WEB" },
      "result": "SUCCESS",
      "createdAt": "2024-01-15T10:30:00"
    }
  ],
  "totalElements": 1000
}
```

---

#### GET /admin/audit/logs/search

감사 로그 검색

**권한 필요**: `admin:audit:read`

#### Query Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| userId | long | 사용자 ID |
| eventType | string | 이벤트 타입 |
| eventCategory | string | 이벤트 카테고리 |
| result | string | 결과 (`SUCCESS`, `FAILURE`) |
| startDate | datetime | 시작일 (ISO 8601) |
| endDate | datetime | 종료일 (ISO 8601) |

---

#### GET /admin/audit/logs/user/{userId}

사용자별 감사 로그 조회

**권한 필요**: `admin:audit:read`

---

#### GET /admin/audit/event-types

사용 가능한 이벤트 타입 목록

**권한 필요**: `admin:audit:read`

#### Response `200 OK`

```json
[
  "LOGIN_SUCCESS",
  "LOGIN_FAILURE",
  "LOGOUT",
  "PASSWORD_CHANGED",
  "ACCOUNT_LOCKED",
  "IP_BLOCKED"
]
```

---

#### GET /admin/audit/event-categories

사용 가능한 이벤트 카테고리 목록

**권한 필요**: `admin:audit:read`

#### Response `200 OK`

```json
[
  "AUTH",
  "ACCOUNT",
  "PERMISSION",
  "OAUTH2",
  "ADMIN",
  "SECURITY"
]
```

---

### 보안 관리 API

#### GET /admin/security/blocked-ips

차단된 IP 목록 조회

**권한 필요**: `admin:security:manage`

#### Response `200 OK`

```json
{
  "content": [
    {
      "id": 1,
      "ipAddress": "192.168.1.100",
      "blockReason": "BRUTE_FORCE",
      "description": "로그인 시도 50회 초과",
      "expiresAt": "2024-01-16T10:30:00",
      "blockedAt": "2024-01-15T10:30:00",
      "blockedBy": 1
    }
  ]
}
```

---

#### POST /admin/security/blocked-ips

IP 차단

**권한 필요**: `admin:security:manage`

#### Request

```json
{
  "ipAddress": "192.168.1.100",
  "reason": "MANUAL",
  "description": "의심스러운 활동",
  "durationMinutes": 60
}
```

#### Response `201 Created`

```json
{
  "id": 1,
  "ipAddress": "192.168.1.100",
  "blockReason": "MANUAL",
  "description": "의심스러운 활동",
  "expiresAt": "2024-01-15T11:30:00",
  "blockedAt": "2024-01-15T10:30:00",
  "blockedBy": 1
}
```

---

#### DELETE /admin/security/blocked-ips/{id}

IP 차단 해제 (ID로)

**권한 필요**: `admin:security:manage`

#### Response `204 No Content`

(응답 본문 없음)

---

#### DELETE /admin/security/blocked-ips/ip/{ipAddress}

IP 차단 해제 (IP 주소로)

**권한 필요**: `admin:security:manage`

#### Response `204 No Content`

(응답 본문 없음)

---

#### GET /admin/security/blocked-ips/check/{ipAddress}

IP 차단 상태 확인

**권한 필요**: `admin:security:manage`

#### Response `200 OK`

```json
{
  "ipAddress": "192.168.1.100",
  "isBlocked": true,
  "blockInfo": {
    "id": 1,
    "blockReason": "BRUTE_FORCE",
    "expiresAt": "2024-01-16T10:30:00"
  }
}
```

---

#### POST /admin/security/accounts/unlock

계정 잠금 해제

**권한 필요**: `admin:security:manage`

#### Request

```json
{
  "username": "user@example.com"
}
```

#### Response `200 OK`

```json
{
  "message": "계정 잠금이 해제되었습니다: user@example.com"
}
```

---

#### GET /admin/security/accounts/status/{username}

계정 로그인 시도 상태 확인

**권한 필요**: `admin:security:manage`

#### Response `200 OK`

```json
{
  "username": "user@example.com",
  "currentAttempts": 3,
  "maxAttempts": 5,
  "isLocked": false,
  "remainingLockTimeSeconds": null
}
```

---

#### GET /admin/security/settings

현재 보안 설정 조회

**권한 필요**: `admin:security:manage`

#### Response `200 OK`

```json
{
  "enableBruteForceProtection": true,
  "maxLoginAttempts": 5,
  "loginAttemptWindowMinutes": 15,
  "accountLockDurationMinutes": 30,
  "enableIpBlocking": true,
  "maxIpLoginAttempts": 10,
  "ipBlockDurationMinutes": 60,
  "permanentBlockThreshold": 50
}
```

---

## 참고

- [시퀀스 다이어그램](SEQUENCE_DIAGRAMS.md) - 상세 API 흐름
- [에러 코드](ERROR_CODES.md) - 에러 코드 상세
