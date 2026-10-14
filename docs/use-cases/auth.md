# 인증 API 유즈케이스

## 개요

사용자 인증 및 세션 관리를 위한 API입니다.

## 유즈케이스 목록

| ID | 유즈케이스 | API Endpoint | 인증 필요 |
|---|---|---|---|
| UC-AUTH-001 | 회원가입 | `POST /auth/signup` | 아니오 |
| UC-AUTH-002 | 로그인 | `POST /auth/login` | 아니오 |
| UC-AUTH-003 | 로그아웃 | `POST /auth/logout` | 예 |
| UC-AUTH-004 | 토큰 갱신 | `POST /auth/refresh` | 아니오 |
| UC-AUTH-005 | 비밀번호 변경 | `POST /auth/password/change` | 예 |
| UC-AUTH-006 | 내 정보 조회 | `GET /users/me` | 예 |
| UC-AUTH-007 | 전체 세션 로그아웃 | `DELETE /users/me/sessions` | 예 |
| UC-AUTH-008 | 특정 세션 로그아웃 | `DELETE /users/me/sessions/{sessionId}` | 예 |

---

## UC-AUTH-001: 회원가입

### 설명
새로운 사용자 계정을 생성합니다.

### 선행조건
- 없음

### 기본 흐름
1. 사용자가 회원가입 정보를 입력합니다.
2. 시스템이 입력 데이터를 검증합니다.
3. 시스템이 로그인 ID와 이메일 중복을 확인합니다.
4. 시스템이 비밀번호를 암호화합니다.
5. 시스템이 사용자 계정을 생성합니다.
6. 시스템이 기본 그룹(USER)을 할당합니다.
7. 시스템이 사용자 정보를 반환합니다 (201 Created).

### 대안 흐름
- **A1: 중복 로그인 ID** → 409 Conflict 반환
- **A2: 중복 이메일** → 409 Conflict 반환
- **A3: 유효성 검증 실패** → 400 Bad Request 반환

### 입력
```json
{
  "loginId": "string (필수, 4-100자)",
  "password": "string (필수, 8-100자)",
  "email": "string (선택, 이메일 형식)",
  "name": "string (선택)",
  "nickname": "string (선택)",
  "phone": "string (선택)"
}
```

### 출력
```json
{
  "id": 1,
  "loginId": "testuser",
  "email": "test@example.com",
  "name": "테스트 사용자",
  "nickname": "테스터",
  "phone": "010-1234-5678",
  "profileImage": null,
  "groups": ["USER"]
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 회원가입 | 유효한 모든 필드 | 201 Created, UserInfoDto 반환 |
| 최소 필드만 | loginId, password만 | 201 Created |
| 중복 로그인 ID | 기존 사용자 loginId | 409 Conflict |
| 중복 이메일 | 기존 사용자 email | 409 Conflict |
| 짧은 로그인 ID | 3자 이하 loginId | 400 Bad Request |
| 짧은 비밀번호 | 7자 이하 password | 400 Bad Request |
| 잘못된 이메일 형식 | "invalid-email" | 400 Bad Request |
| 빈 로그인 ID | "" | 400 Bad Request |
| 빈 비밀번호 | "" | 400 Bad Request |

---

## UC-AUTH-002: 로그인

### 설명
사용자 인증 후 JWT 토큰을 발급합니다.

### 선행조건
- 사용자 계정이 존재해야 함
- 사용자 상태가 ACTIVE여야 함

### 기본 흐름
1. 사용자가 로그인 정보를 입력합니다.
2. 시스템이 사용자를 조회합니다.
3. 시스템이 비밀번호를 검증합니다.
4. 시스템이 세션을 생성합니다.
5. 시스템이 액세스 토큰과 리프레시 토큰을 발급합니다.
6. 시스템이 세션 정보를 캐시에 저장합니다.
7. 시스템이 토큰을 반환합니다 (200 OK).

### 대안 흐름
- **A1: 사용자 없음** → 401 Unauthorized 반환
- **A2: 잘못된 비밀번호** → 401 Unauthorized 반환
- **A3: 비활성 계정** → 401 Unauthorized 반환

### 입력
```json
{
  "loginId": "string (필수)",
  "password": "string (필수)",
  "deviceType": "string (선택)",
  "deviceInfo": "string (선택)"
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
| 정상 로그인 | 올바른 loginId, password | 200 OK, TokenResponseDto 반환 |
| 잘못된 비밀번호 | 올바른 loginId, 틀린 password | 401 Unauthorized |
| 존재하지 않는 사용자 | 없는 loginId | 401 Unauthorized |
| 빈 로그인 ID | "" | 400 Bad Request |
| 빈 비밀번호 | "" | 400 Bad Request |
| 디바이스 정보 포함 | deviceType, deviceInfo 포함 | 200 OK |

---

## UC-AUTH-003: 로그아웃

### 설명
현재 세션을 무효화합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 로그아웃을 요청합니다.
2. 시스템이 토큰에서 세션 ID를 추출합니다.
3. 시스템이 세션을 무효화합니다 (revoked = true).
4. 시스템이 캐시에서 세션을 삭제합니다.
5. 시스템이 204 No Content를 반환합니다.

### 대안 흐름
- **A1: 토큰 없음** → 401 Unauthorized 반환
- **A2: 유효하지 않은 토큰** → 401 Unauthorized 반환

### 입력
- Header: `Authorization: Bearer {accessToken}`

### 출력
- 204 No Content (본문 없음)

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 로그아웃 | 유효한 토큰 | 204 No Content |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |
| 만료된 토큰 | 만료된 accessToken | 401 Unauthorized |
| 잘못된 토큰 형식 | "invalid-token" | 401 Unauthorized |

---

## UC-AUTH-004: 토큰 갱신

### 설명
리프레시 토큰을 사용하여 새 액세스 토큰을 발급합니다.

### 선행조건
- 유효한 리프레시 토큰 필요
- 세션이 무효화되지 않아야 함

### 기본 흐름
1. 사용자가 리프레시 토큰을 제출합니다.
2. 시스템이 리프레시 토큰을 검증합니다.
3. 시스템이 세션 유효성을 확인합니다.
4. 시스템이 새 액세스 토큰을 발급합니다.
5. 시스템이 세션 정보를 업데이트합니다.
6. 시스템이 새 액세스 토큰을 반환합니다 (200 OK).

### 대안 흐름
- **A1: 유효하지 않은 리프레시 토큰** → 401 Unauthorized 반환
- **A2: 만료된 리프레시 토큰** → 401 Unauthorized 반환
- **A3: 무효화된 세션** → 401 Unauthorized 반환

### 입력
```json
{
  "refreshToken": "string (필수)"
}
```

### 출력
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 토큰 갱신 | 유효한 refreshToken | 200 OK, AccessTokenResponseDto 반환 |
| 만료된 리프레시 토큰 | 만료된 refreshToken | 401 Unauthorized |
| 액세스 토큰으로 갱신 시도 | accessToken 전송 | 401 Unauthorized |
| 잘못된 토큰 형식 | "invalid-token" | 401 Unauthorized |
| 빈 리프레시 토큰 | "" | 400 Bad Request |
| 무효화된 세션의 토큰 | 로그아웃된 세션의 토큰 | 401 Unauthorized |

---

## UC-AUTH-005: 비밀번호 변경

### 설명
현재 비밀번호를 확인 후 새 비밀번호로 변경합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 현재 비밀번호와 새 비밀번호를 입력합니다.
2. 시스템이 현재 비밀번호를 검증합니다.
3. 시스템이 새 비밀번호를 암호화합니다.
4. 시스템이 비밀번호를 업데이트합니다.
5. 시스템이 모든 세션을 무효화합니다.
6. 시스템이 204 No Content를 반환합니다.

### 대안 흐름
- **A1: 잘못된 현재 비밀번호** → 401 Unauthorized 반환
- **A2: 새 비밀번호 유효성 검증 실패** → 400 Bad Request 반환

### 입력
```json
{
  "currentPassword": "string (필수)",
  "newPassword": "string (필수, 8-100자)"
}
```

### 출력
- 204 No Content (본문 없음)

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 비밀번호 변경 | 올바른 현재 비밀번호, 유효한 새 비밀번호 | 204 No Content |
| 잘못된 현재 비밀번호 | 틀린 currentPassword | 401 Unauthorized |
| 짧은 새 비밀번호 | 7자 이하 newPassword | 400 Bad Request |
| 빈 현재 비밀번호 | "" | 400 Bad Request |
| 빈 새 비밀번호 | "" | 400 Bad Request |

---

## UC-AUTH-006: 내 정보 조회

### 설명
현재 로그인한 사용자의 정보를 조회합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 내 정보 조회를 요청합니다.
2. 시스템이 토큰에서 사용자 ID를 추출합니다.
3. 시스템이 사용자 정보를 조회합니다.
4. 시스템이 그룹 정보를 조회합니다.
5. 시스템이 사용자 정보를 반환합니다 (200 OK).

### 입력
- Header: `Authorization: Bearer {accessToken}`

### 출력
```json
{
  "id": 1,
  "loginId": "testuser",
  "email": "test@example.com",
  "name": "테스트 사용자",
  "nickname": "테스터",
  "phone": "010-1234-5678",
  "profileImage": null,
  "groups": ["USER"]
}
```

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 조회 | 유효한 토큰 | 200 OK, UserInfoDto 반환 |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |
| 만료된 토큰 | 만료된 accessToken | 401 Unauthorized |

---

## UC-AUTH-007: 전체 세션 로그아웃

### 설명
현재 세션을 제외한 모든 세션을 무효화합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 전체 세션 로그아웃을 요청합니다.
2. 시스템이 현재 세션을 제외한 모든 세션을 무효화합니다.
3. 시스템이 캐시에서 세션들을 삭제합니다.
4. 시스템이 204 No Content를 반환합니다.

### 입력
- Header: `Authorization: Bearer {accessToken}`

### 출력
- 204 No Content (본문 없음)

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 전체 로그아웃 | 유효한 토큰 | 204 No Content |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |

---

## UC-AUTH-008: 특정 세션 로그아웃

### 설명
특정 세션을 무효화합니다.

### 선행조건
- 유효한 액세스 토큰 필요

### 기본 흐름
1. 사용자가 특정 세션 ID로 로그아웃을 요청합니다.
2. 시스템이 해당 세션을 무효화합니다.
3. 시스템이 캐시에서 세션을 삭제합니다.
4. 시스템이 204 No Content를 반환합니다.

### 입력
- Header: `Authorization: Bearer {accessToken}`
- Path: `/users/me/sessions/{sessionId}`

### 출력
- 204 No Content (본문 없음)

### 테스트 시나리오

| 시나리오 | 입력 | 예상 결과 |
|---|---|---|
| 정상 세션 로그아웃 | 유효한 토큰, 유효한 sessionId | 204 No Content |
| 토큰 없음 | Authorization 헤더 없음 | 401 Unauthorized |
| 존재하지 않는 세션 | 없는 sessionId | 204 No Content (또는 404) |

---

## 에러 코드

| HTTP Status | 에러 코드 | 설명 |
|---|---|---|
| 400 | BAD_REQUEST | 입력 데이터 유효성 검증 실패 |
| 401 | AUTHENTICATION_FAILED | 인증 실패 (비밀번호 오류 등) |
| 401 | INVALID_TOKEN | 유효하지 않은 토큰 |
| 401 | TOKEN_EXPIRED | 토큰 만료 |
| 401 | SESSION_NOT_FOUND | 세션이 존재하지 않음 |
| 403 | FORBIDDEN | 권한 없음 |
| 409 | DUPLICATE_USER | 중복 사용자 |

---

## 시퀀스 다이어그램

### 로그인 흐름

```
Client        AuthController      AuthMappingService      AuthBizService        UserEntityService /        Cache
  |                 |                     |                     |             UserSessionEntityService       |
  |-- POST /auth/login -->|               |                     |                      |                      |
  |                 |-- login(dto, ip) -->|                     |                      |                      |
  |                 |                     |-- login() --------->|                      |                      |
  |                 |                     |                     |-- getActiveUserByLoginId() -->|            |
  |                 |                     |                     |<-- UserDto -----------|                      |
  |                 |                     |                     |-- validatePassword()  |                      |
  |                 |                     |                     |-- generateTokens()    |                      |
  |                 |                     |                     |-- createSession(record) ---->|             |
  |                 |                     |                     |-- cacheSession() -----|--------------------->|
  |                 |                     |<-- TokenResponseDto |                      |                      |
  |                 |<-- TokenResponseModel                     |                      |                      |
  |<-- 200 OK (tokens) ---|               |                     |                      |                      |
```

### 토큰 갱신 흐름

```
Client        AuthController      AuthMappingService      AuthBizService      UserSessionEntityService    Cache
  |                 |                     |                     |                      |                  |
  |-- POST /auth/refresh -->|             |                     |                      |                  |
  |                 |-- refresh(token) -->|                     |                      |                  |
  |                 |                     |-- refresh() ------->|                      |                  |
  |                 |                     |                     |-- validateToken() ---|                  |
  |                 |                     |                     |-- getUnrevokedBySessionId() -->|         |
  |                 |                     |                     |<-- UserSessionRecordDto ------|         |
  |                 |                     |                     |-- generateAccessToken()                 |
  |                 |                     |                     |-- refreshAccessToken() ------>|         |
  |                 |                     |                     |-- deleteCache() -----|----------------->|
  |                 |                     |<-- AccessTokenResponseDto                  |                  |
  |                 |<-- AccessTokenResponseModel               |                      |                  |
  |<-- 200 OK (newToken) ---|             |                     |                      |                  |
```
