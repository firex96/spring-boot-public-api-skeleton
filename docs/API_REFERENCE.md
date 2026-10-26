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
  "resultMessage": "Bad Request Exception"
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

#### Response `202 Accepted` — 2차 인증을 켠 계정

세션을 만들지 않고 2단계 challenge 를 준다. `POST /auth/login/mfa` 로 마친다.

```json
{
  "mfaToken": "q3Hc…(43자)",
  "expiresIn": 300,
  "methods": ["totp", "recovery"]
}
```

`methods` 의 `recovery` 는 남은 복구 코드가 있을 때만 있다.

#### Errors

| 코드 | 설명 |
|------|------|
| 401 | 인증 실패 (잘못된 아이디/비밀번호) |
| 423 | 계정 잠금 (로그인 시도 초과) |

---

### POST /auth/login/mfa

로그인 2단계 — `POST /auth/login`·`POST /auth/oauth2/{provider}/login` 이 준 `202` challenge 를 마친다. 성공하면 challenge 는 사라진다(1회).

#### Request

```json
{
  "mfaToken": "q3Hc…",
  "method": "totp",
  "code": "123456"
}
```

복구 코드면 `"method": "recovery", "code": "ABCD-EFGH"`(대소문자·하이픈 무시, 한 번만).

#### Response `200 OK`

`POST /auth/login` 의 `200` 과 같은 `TokenResponseModel`.

#### Errors

| 코드 | 설명 |
|------|------|
| 400 `MFA001` | 코드 불일치 — 실패는 계정 잠금 카운터를 올리고, challenge 하나에서 5번 틀리면 challenge 가 지워진다 |
| 401 `MFA002` | challenge 없음(만료·사용·한도 초과) — 다시 로그인 |
| 423 | 계정 잠금 — 코드를 보기 전에 확인한다 |

---

### 2차 인증 등록·관리 (`/user/mfa`, Bearer)

| 메서드·경로 | 요청 | 응답 | 오류 |
|---|---|---|---|
| `GET /user/mfa` | — | `{"enabled": true, "recoveryCodesLeft": 9}` | — |
| `POST /user/mfa/totp/setup` | — | `{"secret": "JBSW Y3DP …", "otpauthUri": "otpauth://totp/Skeleton:user1?secret=…&issuer=Skeleton&digits=6&period=30&algorithm=SHA1"}` — 아직 켜지지 않는다 | 409 `MFA003` |
| `POST /user/mfa/totp/activate` | `{"code": "123456"}` | `{"recoveryCodes": ["ABCD-EFGH", …(10개)]}` — 원본은 이 응답에서만. 켜고 **다른 세션을 모두 폐기**한다 | 400 `MFA001` · 404 `MFA005` · 409 `MFA003` · 429 `MFA006` |
| `POST /user/mfa/recovery-codes` | `{"code": "123456"}`(TOTP) | `{"recoveryCodes": [… 10개]}` — 이전 코드는 모두 무효 | 400 · 409 `MFA004` · 429 |
| `POST /user/mfa/disable` | `{"method": "totp" \| "recovery", "code": "…"}` | `204` | 400 · 409 `MFA004` · 429 |

재인증은 비밀번호가 아니라 현재 코드다(소셜 전용 사용자는 비밀번호가 없다). 관리 API 의 실패는 로그인 잠금과 별개로 5번/15분 제한이다.

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

비밀번호 변경 — 성공하면 **현재 세션을 포함한 모든 세션**을 폐기한다(다시 로그인해야 한다).

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

소셜 로그인 시작 — **브라우저가 이 경로로 직접 이동한다**(fetch 로 부르지 않는다).

서버가 state(32바이트 난수)를 발급해 이 브라우저 세션(`SKELETON_SESSION` 쿠키)에 묶고, provider 인가 화면으로 302 한다. state 는 Redis `browser:social-state:{state}` 에 5분 동안 있고 콜백에서 한 번만 소비된다.

#### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| provider | string | 소셜 제공자 (`GOOGLE`, `KAKAO`, `NAVER`) |

#### Response `302 Found`

`Location: https://accounts.google.com/o/oauth2/v2/auth?...&state=...` · `Set-Cookie: SKELETON_SESSION=...; HttpOnly; SameSite=Lax`

---

### GET /auth/oauth2/{provider}/callback

소셜 로그인 콜백 (리다이렉트)

OAuth Provider로부터의 콜백을 처리합니다. state 를 먼저 소비하고, 성공하면 **토큰을 URL 에 싣지 않고** 서버에 60초 동안 맡긴 뒤(교환 대기) 브라우저 세션에 로그인시키고 쿼리 없이 리다이렉트합니다. 세션 ID 는 이때 바뀝니다(세션 고정 방지).

#### Query Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| code | string | Authorization Code |
| state | string | `authorize` 가 이 브라우저 세션에 발급한 값. 세션·provider 가 다르거나, 만료되거나, 이미 쓰였으면 코드 교환 없이 `/demo/auth/callback?error=invalid_state` 로 302 한다 |

#### Response `302 Redirect`

성공: `/demo/auth/callback` (쿼리 없음) — 페이지가 `POST /auth/session/token` 으로 토큰을 받는다
실패: `/demo/auth/callback?error=...&error_description=...` (`invalid_state` 포함)

---

### POST /auth/session/token

소셜 콜백 토큰 교환 — 콜백이 맡겨 둔 토큰을 **한 번만** 돌려준다. 브라우저 체인(세션 쿠키 `SKELETON_SESSION` + CSRF 헤더)을 탄다.

#### Headers

| 헤더 | 설명 |
|------|------|
| Cookie | `SKELETON_SESSION=...` (콜백이 바꾼 세션) |
| X-CSRF-TOKEN | 데모 콜백 페이지의 `data-token` 값 (헤더 이름은 `data-header`) |

#### Response `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### Error Responses

| 상태 | 설명 |
|------|------|
| 401 | 로그인하지 않은 세션, 교환할 토큰 없음(이미 꺼냄·60초 만료) — `ErrorResponseModel` |
| 403 | CSRF 헤더 없음·불일치 — `ErrorResponseModel` |

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

비밀번호 변경 — 성공하면 **이 요청의 세션을 뺀** 나머지 세션을 폐기한다(다른 기기는 다시 로그인해야 하고, 이 기기는 계속 쓴다).

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

Authorization 요청 (Authorization Code Flow 시작) — **브라우저가 이동하는 경로**다. 브라우저 체인(세션 쿠키 `SKELETON_SESSION`)을 타며, 로그인 판정은 브라우저 세션으로만 한다(Bearer 헤더는 읽지 않는다).

#### Query Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| response_type | Y | `code` |
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope | N | 요청 범위 (space-separated) |
| state | N | 클라이언트의 CSRF 방지 값 — 그대로 돌려준다 |
| code_challenge | N | PKCE code challenge |
| code_challenge_method | N | `plain` 또는 `S256` |
| nonce | N | ID Token replay 방지용 |

#### Response

| 상황 | 응답 |
|------|------|
| client 불량 | `401` JSON `invalid_client` — 리다이렉트하지 않는다 |
| redirect_uri 불량 | `400` JSON `invalid_request` — 리다이렉트하지 않는다 |
| 그 밖의 파라미터 오류 | `302 redirect_uri?error=...&state=...` |
| 브라우저 세션 미로그인 | `302 /oauth2/login` — 원래 authorize URL 은 서버가 다시 조립해 **세션에** 둔다(쿼리로 넘기지 않는다) |
| 로그인 · 1st party | `302 redirect_uri?code=xxx&state=yyy` |
| 로그인 · 3rd party | `302 /oauth2/consent?...` (동의 화면) |

---

### GET /oauth2/login · POST /oauth2/login

인가 서버 로그인 화면(HTML). `POST` 는 폼(`loginId`, `password`, `_csrf`)이다. `POST /auth/login` 과 같은 로그인 보호(IP 차단·계정 잠금·실패 집계)를 지나고, 토큰을 내주지 않고 브라우저 세션에 로그인시킨다(세션 ID 교체).

성공: `302` 세션에 둔 authorize URL(없으면 `/oauth2/login?success`) · 실패: `200` 같은 화면에 메시지

---

### GET /oauth2/consent

동의 화면(HTML) — 클라이언트 이름과 요청 scope 를 보여 주고 승인·거부 폼을 낸다.

**브라우저 세션 로그인 필요** (없으면 `302 /oauth2/login`)

#### Query Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope | N | 요청 범위 |
| state | N | 클라이언트의 state |
| code_challenge · code_challenge_method · nonce | N | authorize 에서 받은 값 그대로 |

검증 실패(client·redirect_uri·scope·PKCE)는 `oauth2/error` HTML(`invalid_client` 401, 그 외 400)이고 리다이렉트하지 않는다.

동의 결과는 저장하지 않는다 — third-party 클라이언트는 authorize 마다 이 화면을 거친다.

---

### POST /oauth2/consent

동의 처리 — 동의 화면의 폼 제출.

**브라우저 세션 로그인 + CSRF(`_csrf`) 필요** (CSRF 없음 `403` `ErrorResponseModel`)

#### Form Parameters

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| client_id | Y | 클라이언트 ID |
| redirect_uri | Y | 리다이렉트 URI |
| scope · state · code_challenge · code_challenge_method · nonce | N | 동의 화면의 hidden 값 |
| approve | Y | 동의 여부 (`true`/`false`) |
| _csrf | Y | 화면의 CSRF 토큰 |

#### Response

동의: `302 redirect_uri?code=xxx&state=yyy`
거부: `302 redirect_uri?error=access_denied&error_description=...&state=yyy`
검증 실패: `400`/`401` HTML 오류 화면, Location 없음, 코드 미발급

---

### POST /oauth2/token

토큰 발급

**Content-Type: application/x-www-form-urlencoded**

#### 클라이언트 인증 (`/oauth2/token`·`/oauth2/introspect`·`/oauth2/revoke` 공통)

시크릿은 **필수**다 — 모든 클라이언트는 시크릿을 갖고, 시크릿 없이 `client_id` 만으로는 인증되지 않는다. 두 방식 중 **하나만** 쓴다.

| 방식 | 보내는 법 |
|------|-----------|
| `client_secret_basic` | `Authorization: Basic base64(urlencode(client_id) ":" urlencode(client_secret))` (RFC 6749 §2.3.1) |
| `client_secret_post` | 폼 `client_id`·`client_secret` |

| 실패 | 응답 |
|------|------|
| 시크릿 없음·불일치·깨진 Basic | `401` `invalid_client` + `WWW-Authenticate: Basic realm="oauth2"` |
| Basic 과 폼 `client_secret` 동시 전송, 폼 `client_id` 가 Basic 과 다름 | `400` `invalid_request` |

#### Authorization Code Grant

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| grant_type | Y | `authorization_code` |
| code | Y | Authorization Code |
| redirect_uri | Y | 리다이렉트 URI |
| client_id | Y(post) | 클라이언트 ID (`client_secret_basic` 이면 헤더) |
| client_secret | Y(post) | 클라이언트 시크릿 (`client_secret_basic` 이면 헤더) |
| code_verifier | N | PKCE code verifier |

#### Refresh Token Grant

| 파라미터 | 필수 | 설명 |
|---------|------|------|
| grant_type | Y | `refresh_token` |
| refresh_token | Y | Refresh Token |
| client_id | Y(post) | 클라이언트 ID (`client_secret_basic` 이면 헤더) |
| client_secret | Y(post) | 클라이언트 시크릿 (`client_secret_basic` 이면 헤더) |
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

OpenID Connect Discovery — `*_supported` 값은 실제 지원값 상수(`OAuth2AuthorizationBizService.SUPPORTED_*`, `IdTokenBizService.SUPPORTED_*`)에서 만든다. 광고한 값마다 실제 동작을 `DiscoveryConformanceE2ETest` 가 대조한다.

#### Response `200 OK`

```json
{
  "issuer": "https://api.example.com",
  "authorization_endpoint": "https://api.example.com/oauth2/authorize",
  "token_endpoint": "https://api.example.com/oauth2/token",
  "userinfo_endpoint": "https://api.example.com/oauth2/userinfo",
  "jwks_uri": "https://api.example.com/.well-known/jwks.json",
  "revocation_endpoint": "https://api.example.com/oauth2/revoke",
  "introspection_endpoint": "https://api.example.com/oauth2/introspect",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "scopes_supported": ["openid", "profile", "email", "offline_access"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post"],
  "code_challenge_methods_supported": ["S256", "plain"],
  "claims_supported": ["iss", "sub", "aud", "iat", "exp", "jti", "auth_time", "nonce", "at_hash", "name", "nickname", "picture", "updated_at", "email", "email_verified"]
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

사용자 정보 조회 (OIDC Core §5.3)

**`Authorization: Bearer <이 인가 서버가 발급한 access 토큰>`** — `openid` scope 를 받은 활성 토큰이어야 한다. `POST /auth/login` 의 1st-party 토큰, client_credentials 토큰(사용자 없음), 폐기·만료 토큰은 받지 않는다.

클레임은 토큰의 scope 만큼만 싣는다 — `profile` → `name`·`nickname`·`picture`·`updated_at`, `email` → `email`·`email_verified`. `email_verified` 는 현재 이메일을 인증 링크로 확인한 기록이 있을 때만 `true` 다.

#### Response `200 OK` (scope `openid profile email`)

```json
{
  "sub": "12345",
  "name": "홍길동",
  "nickname": "길동이",
  "picture": "https://cdn.example.com/profiles/12345.jpg",
  "email": "user@example.com",
  "email_verified": true,
  "updated_at": 1705312200
}
```

#### Error Responses (RFC 6750 §3)

| 상태 | `WWW-Authenticate` | 본문 `error` |
|------|-------------------|--------------|
| 401 | `Bearer error="invalid_token"` | `invalid_token` — 토큰 없음·서명/만료 불량·발급 기록 없음·폐기·사용자 없음 |
| 403 | `Bearer error="insufficient_scope", scope="openid"` | `insufficient_scope` |

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

#### DELETE /admin/security/accounts/{userId}/mfa

2차 인증 초기화 — 인증 앱·복구 코드를 모두 잃은 사용자. 등록·복구 코드를 지우고 **그 사용자의 세션을 모두 폐기**한다. 감사 `MFA_RESET_BY_ADMIN`(주체는 관리자).

**권한 필요**: `admin:security:manage`

#### Response `204 No Content`

#### Errors

| 코드 | 설명 |
|------|------|
| 403 | 권한 없음 |
| 404 | 2차 인증 등록이 없다 |

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
