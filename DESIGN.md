# Public API Skeleton - 인증/인가 설계 문서

## 1. 아키텍처 개요

### 1.1 시스템 역할

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Public API Server                                  │
│                                                                             │
│  ┌─────────────────────┐    ┌─────────────────────┐                        │
│  │   OAuth Client      │    │ OAuth Authorization │                        │
│  │   (소셜 로그인)      │    │     Server          │                        │
│  │                     │    │  (토큰 발급/검증)    │                        │
│  │  - 네이버 로그인     │    │                     │                        │
│  │  - 카카오 로그인     │    │  - Access Token     │                        │
│  │  - 구글 로그인       │    │  - Refresh Token    │                        │
│  └─────────────────────┘    └─────────────────────┘                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
         │                              │
         │ 외부 인증                     │ 토큰 발급
         ▼                              ▼
┌─────────────────┐            ┌─────────────────────┐
│  소셜 Provider   │            │    Client Apps      │
│  - Naver        │            │  - Web App          │
│  - Kakao        │            │  - Mobile App       │
│  - Google       │            │  - 3rd Party (향후) │
└─────────────────┘            └─────────────────────┘
```

### 1.2 인증 흐름

```
[일반 로그인]
User → POST /auth/login (id, password)
     → Server: 비밀번호 검증
     → Server: JWT Access Token + Refresh Token 발급
     → Server: DB에 세션 저장 + Redis 캐시
     → Response: { accessToken, refreshToken, expiresIn }

[소셜 로그인]
User → GET /auth/oauth2/{provider} (redirect)
     → Provider: 인증 페이지
     → Provider: callback with code
     → Server: code → token 교환
     → Server: Provider API로 사용자 정보 조회
     → Server: 사용자 생성/연동
     → Server: JWT Token 발급 + DB 저장 + Redis 캐시
     → Response: { accessToken, refreshToken, expiresIn }

[API 요청]
User → API Request (Authorization: Bearer {accessToken})
     → Server: JWT 서명/만료 검증
     → Server: Redis 캐시에서 세션 조회 (Cache Hit)
     → Server: 캐시 미스 시 DB 조회 → Redis 캐시 갱신
     → Server: 세션 유효성 확인 (revoked 여부)
     → Server: API 처리
     → Response

[토큰 갱신]
User → POST /auth/refresh (refreshToken)
     → Server: Refresh Token 검증
     → Server: 새 Access Token 발급
     → Server: DB + Redis 세션 갱신
     → Response: { accessToken, expiresIn }

[로그아웃/세션 강제 종료]
Admin/User → DELETE /sessions/{sessionId}
     → Server: DB 세션 revoked 처리
     → Server: Redis 캐시 삭제 (즉시 무효화)
```

### 1.3 세션 저장소 구조

```
┌─────────────────────────────────────────────────────────────┐
│                      API Request                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Redis (캐시 레이어)                       │
│                                                             │
│  Key: session:{sessionId}                                   │
│  Value: { userId, groups, permissions, expiresAt, revoked } │
│  TTL: Access Token 만료시간과 동일                           │
│                                                             │
│  장점: 빠른 조회, DB 부하 감소                               │
│  용도: 매 API 요청 시 세션 검증                              │
└─────────────────────────────────────────────────────────────┘
                    │ Cache Miss
                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    DB (영구 저장소)                          │
│                                                             │
│  Table: user_sessions                                       │
│                                                             │
│  장점: 영속성, 세션 이력 관리                                │
│  용도: 세션 생성/삭제, 감사 로그, 동시 접속 관리             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. DB 테이블 설계

> **Note**: 외래키 제약조건은 사용하지 않음. 참조 무결성은 애플리케이션 레벨에서 관리.

### 2.1 사용자 관련 테이블

```sql
-- 사용자 기본 정보
CREATE TABLE users (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    login_id        NVARCHAR(100) NOT NULL,
    password_hash   NVARCHAR(255) NULL,                 -- 비밀번호 (소셜 전용은 NULL)
    email           NVARCHAR(255) NULL,
    phone           NVARCHAR(20) NULL,
    name            NVARCHAR(100) NULL,
    nickname        NVARCHAR(100) NULL,
    profile_image   NVARCHAR(500) NULL,
    status          NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, SUSPENDED, DELETED
    last_login_at   DATETIME2 NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    created_by      NVARCHAR(100) NULL,
    updated_by      NVARCHAR(100) NULL
);

CREATE UNIQUE INDEX UX_users_login_id ON users(login_id);
CREATE INDEX IX_users_email ON users(email);
CREATE INDEX IX_users_status ON users(status);

-- 소셜 계정 연동 정보
CREATE TABLE user_social_accounts (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL,                    -- → users.id
    provider        NVARCHAR(20) NOT NULL,              -- NAVER, KAKAO, GOOGLE
    provider_id     NVARCHAR(255) NOT NULL,             -- Provider별 고유 ID
    email           NVARCHAR(255) NULL,
    name            NVARCHAR(100) NULL,
    profile_image   NVARCHAR(500) NULL,
    access_token    NVARCHAR(1000) NULL,                -- Provider Access Token (선택적 저장)
    refresh_token   NVARCHAR(1000) NULL,
    token_expires_at DATETIME2 NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE INDEX IX_user_social_accounts_user_id ON user_social_accounts(user_id);
CREATE UNIQUE INDEX UX_user_social_accounts_provider ON user_social_accounts(provider, provider_id);
```

### 2.2 토큰/세션 관리 테이블

```sql
-- Token Store (JWT 메타정보 + 세션 관리)
-- Redis 캐시와 함께 사용, DB는 영구 저장소 역할
CREATE TABLE user_sessions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL,                    -- → users.id
    session_id      NVARCHAR(64) NOT NULL,              -- UUID, JWT jti claim에 포함
    device_type     NVARCHAR(20) NULL,                  -- WEB, MOBILE_IOS, MOBILE_ANDROID
    device_info     NVARCHAR(500) NULL,                 -- User-Agent 등
    ip_address      NVARCHAR(45) NULL,
    refresh_token_hash NVARCHAR(255) NOT NULL,          -- Refresh Token 해시값
    access_token_issued_at DATETIME2 NOT NULL,
    refresh_token_issued_at DATETIME2 NOT NULL,
    access_token_expires_at DATETIME2 NOT NULL,
    refresh_token_expires_at DATETIME2 NOT NULL,
    last_activity_at DATETIME2 NOT NULL,
    is_revoked      BIT NOT NULL DEFAULT 0,             -- 강제 로그아웃 여부
    revoked_at      DATETIME2 NULL,
    revoked_reason  NVARCHAR(100) NULL,                 -- LOGOUT, FORCED, PASSWORD_CHANGED, etc.
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE INDEX IX_user_sessions_user_id ON user_sessions(user_id);
CREATE UNIQUE INDEX UX_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX IX_user_sessions_refresh_expires ON user_sessions(refresh_token_expires_at);
CREATE INDEX IX_user_sessions_active ON user_sessions(user_id, is_revoked) WHERE is_revoked = 0;
```

### 2.3 그룹 기반 권한 테이블 (독립적 설계)

```sql
-- 그룹 정의 (독립적 그룹)
CREATE TABLE groups (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(50) NOT NULL,              -- SUPER_ADMIN, ADMIN, USER
    name            NVARCHAR(100) NOT NULL,
    description     NVARCHAR(500) NULL,
    is_system       BIT NOT NULL DEFAULT 0,             -- 시스템 기본 그룹 (삭제 불가)
    is_default      BIT NOT NULL DEFAULT 0,             -- 신규 가입 시 기본 그룹
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE UNIQUE INDEX UX_groups_code ON groups(code);

-- 기본 그룹 데이터
-- INSERT INTO groups (code, name, is_system, is_default) VALUES
-- ('SUPER_ADMIN', '최고 관리자', 1, 0),
-- ('ADMIN', '관리자', 1, 0),
-- ('USER', '사용자', 1, 1);  -- 기본 그룹

-- 사용자-그룹 매핑 (다대다: 사용자는 여러 그룹 소속 가능)
CREATE TABLE user_groups (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL,                    -- → users.id
    group_id        INT NOT NULL,                       -- → groups.id
    assigned_at     DATETIME2 NOT NULL DEFAULT GETDATE(),
    assigned_by     BIGINT NULL,                        -- → users.id (할당한 관리자)
    expires_at      DATETIME2 NULL                      -- 임시 그룹 부여 시
);

CREATE INDEX IX_user_groups_user_id ON user_groups(user_id);
CREATE INDEX IX_user_groups_group_id ON user_groups(group_id);
CREATE UNIQUE INDEX UX_user_groups ON user_groups(user_id, group_id);

-- 권한(Permission) 정의
CREATE TABLE permissions (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(100) NOT NULL,             -- user:read, user:write, admin:manage
    name            NVARCHAR(100) NOT NULL,
    description     NVARCHAR(500) NULL,
    resource_type   NVARCHAR(50) NULL,                  -- USER, PRODUCT, ORDER, etc.
    action          NVARCHAR(50) NULL,                  -- READ, WRITE, DELETE, MANAGE
    is_system       BIT NOT NULL DEFAULT 0,             -- 시스템 권한 (삭제 불가)
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE UNIQUE INDEX UX_permissions_code ON permissions(code);
CREATE INDEX IX_permissions_resource_type ON permissions(resource_type);

-- 기본 권한 데이터
-- INSERT INTO permissions (code, name, resource_type, action, is_system) VALUES
-- ('user:read', '사용자 정보 조회', 'USER', 'READ', 1),
-- ('user:write', '사용자 정보 수정', 'USER', 'WRITE', 1),
-- ('user:delete', '사용자 삭제', 'USER', 'DELETE', 1),
-- ('admin:user:manage', '사용자 관리', 'USER', 'MANAGE', 1),
-- ('admin:group:manage', '그룹 관리', 'GROUP', 'MANAGE', 1),
-- ('admin:system:manage', '시스템 관리', 'SYSTEM', 'MANAGE', 1);

-- 그룹-권한 매핑 (그룹 단위로 권한 관리)
CREATE TABLE group_permissions (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    group_id        INT NOT NULL,                       -- → groups.id
    permission_id   INT NOT NULL,                       -- → permissions.id
    granted_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    granted_by      BIGINT NULL                         -- → users.id
);

CREATE INDEX IX_group_permissions_group_id ON group_permissions(group_id);
CREATE INDEX IX_group_permissions_permission_id ON group_permissions(permission_id);
CREATE UNIQUE INDEX UX_group_permissions ON group_permissions(group_id, permission_id);
```

**그룹 기반 권한 조회:**
```sql
-- 사용자의 모든 권한 조회 (소속 그룹들의 권한 합집합)
SELECT DISTINCT p.code, p.name, p.resource_type, p.action
FROM permissions p
INNER JOIN group_permissions gp ON p.id = gp.permission_id
INNER JOIN user_groups ug ON gp.group_id = ug.group_id
WHERE ug.user_id = @userId
  AND (ug.expires_at IS NULL OR ug.expires_at > GETDATE());

-- 사용자의 그룹 목록 조회
SELECT g.code, g.name
FROM groups g
INNER JOIN user_groups ug ON g.id = ug.group_id
WHERE ug.user_id = @userId
  AND (ug.expires_at IS NULL OR ug.expires_at > GETDATE());
```

### 2.4 리소스 기반 권한 제어 (향후 확장용)

```sql
-- 리소스 타입 정의
CREATE TABLE resource_types (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(50) NOT NULL,              -- USER, PRODUCT, ORDER, CONTENT
    name            NVARCHAR(100) NOT NULL,
    description     NVARCHAR(500) NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE UNIQUE INDEX UX_resource_types_code ON resource_types(code);

-- 리소스 인스턴스 (특정 리소스에 대한 권한 제어)
CREATE TABLE resources (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    resource_type_id INT NOT NULL,                      -- → resource_types.id
    resource_key    NVARCHAR(255) NOT NULL,             -- 실제 리소스 ID (예: product_123)
    name            NVARCHAR(255) NULL,
    owner_user_id   BIGINT NULL,                        -- → users.id (리소스 소유자)
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE INDEX IX_resources_type_id ON resources(resource_type_id);
CREATE UNIQUE INDEX UX_resources_type_key ON resources(resource_type_id, resource_key);
CREATE INDEX IX_resources_owner ON resources(owner_user_id);

-- 리소스-그룹 권한 (특정 리소스에 대한 특정 그룹 권한)
CREATE TABLE resource_group_permissions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    resource_id     BIGINT NOT NULL,                    -- → resources.id
    group_id        INT NOT NULL,                       -- → groups.id
    permission_id   INT NOT NULL,                       -- → permissions.id
    granted_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    granted_by      BIGINT NULL                         -- → users.id
);

CREATE INDEX IX_rgp_resource_id ON resource_group_permissions(resource_id);
CREATE INDEX IX_rgp_group_id ON resource_group_permissions(group_id);
CREATE UNIQUE INDEX UX_rgp ON resource_group_permissions(resource_id, group_id, permission_id);
```

### 2.5 OAuth Client 관리 (제3자 앱 확장용)

```sql
-- OAuth 클라이언트 등록 (향후 제3자 앱 지원)
CREATE TABLE oauth_clients (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    client_id       NVARCHAR(64) NOT NULL,              -- 클라이언트 식별자
    client_secret   NVARCHAR(255) NOT NULL,             -- 클라이언트 시크릿 (해시)
    name            NVARCHAR(100) NOT NULL,
    description     NVARCHAR(500) NULL,
    redirect_uris   NVARCHAR(MAX) NOT NULL,             -- JSON array of redirect URIs
    allowed_scopes  NVARCHAR(MAX) NULL,                 -- JSON array of allowed scopes
    grant_types     NVARCHAR(500) NOT NULL,             -- authorization_code, refresh_token, client_credentials
    is_active       BIT NOT NULL DEFAULT 1,
    owner_user_id   BIGINT NULL,                        -- → users.id (클라이언트 등록자)
    access_token_ttl INT NOT NULL DEFAULT 3600,         -- Access Token TTL (초)
    refresh_token_ttl INT NOT NULL DEFAULT 604800,      -- Refresh Token TTL (초, 7일)
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE UNIQUE INDEX UX_oauth_clients_client_id ON oauth_clients(client_id);
CREATE INDEX IX_oauth_clients_owner ON oauth_clients(owner_user_id);

-- OAuth 인가 코드 (Authorization Code Grant)
CREATE TABLE oauth_authorization_codes (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(64) NOT NULL,
    client_id       BIGINT NOT NULL,                    -- → oauth_clients.id
    user_id         BIGINT NOT NULL,                    -- → users.id
    redirect_uri    NVARCHAR(500) NOT NULL,
    scope           NVARCHAR(500) NULL,
    expires_at      DATETIME2 NOT NULL,
    is_used         BIT NOT NULL DEFAULT 0,
    created_at      DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE UNIQUE INDEX UX_oauth_codes_code ON oauth_authorization_codes(code);
CREATE INDEX IX_oauth_codes_client_id ON oauth_authorization_codes(client_id);
CREATE INDEX IX_oauth_codes_expires ON oauth_authorization_codes(expires_at);
```

---

## 3. Redis 캐시 설계

### 3.1 세션 캐시

```
Key Pattern: session:{sessionId}
TTL: Access Token 만료시간 (기본 1시간)

Value (JSON):
{
  "userId": 12345,
  "sessionId": "abc-123-def",
  "groups": ["USER", "ADMIN"],
  "permissions": ["user:read", "user:write", "admin:user:manage"],
  "deviceType": "WEB",
  "ipAddress": "192.168.1.100",
  "issuedAt": 1704067200,
  "expiresAt": 1704070800,
  "revoked": false
}
```

### 3.2 사용자 권한 캐시

```
Key Pattern: user:permissions:{userId}
TTL: 5분 (권한 변경 시 즉시 무효화)

Value (JSON):
{
  "groups": ["USER", "ADMIN"],
  "permissions": ["user:read", "user:write", "admin:user:manage"]
}
```

### 3.3 캐시 무효화 전략

| 이벤트 | 캐시 무효화 |
|--------|-------------|
| 로그아웃 | `session:{sessionId}` 삭제 |
| 세션 강제 종료 | `session:{sessionId}` 삭제 |
| 비밀번호 변경 | 해당 사용자 모든 `session:*` 삭제 |
| 그룹 변경 | `user:permissions:{userId}` 삭제 |
| 그룹 권한 변경 | 해당 그룹 소속 모든 사용자 `user:permissions:*` 삭제 |

### 3.4 캐시 조회 로직 (Pseudo Code)

```kotlin
fun validateSession(sessionId: String): SessionInfo? {
    // 1. Redis 캐시 조회
    val cached = redisTemplate.opsForValue().get("session:$sessionId")
    if (cached != null) {
        val session = parseSession(cached)
        if (session.revoked) return null
        if (session.expiresAt < now()) return null
        return session
    }

    // 2. Cache Miss → DB 조회
    val dbSession = sessionRepository.findBySessionId(sessionId)
        ?: return null

    if (dbSession.isRevoked) return null
    if (dbSession.accessTokenExpiresAt < now()) return null

    // 3. 권한 정보 조회
    val permissions = permissionRepository.findByUserId(dbSession.userId)

    // 4. Redis 캐시 저장
    val sessionInfo = SessionInfo(
        userId = dbSession.userId,
        sessionId = sessionId,
        groups = permissions.groups,
        permissions = permissions.codes,
        expiresAt = dbSession.accessTokenExpiresAt,
        revoked = false
    )

    val ttl = Duration.between(now(), dbSession.accessTokenExpiresAt)
    redisTemplate.opsForValue().set("session:$sessionId", sessionInfo, ttl)

    return sessionInfo
}
```

---

## 4. JWT Token 설계

### 4.1 Access Token Payload

```json
{
  "sub": "12345",                // Subject: 사용자 ID (users.id)
  "jti": "session_abc123",       // JWT ID: 세션 ID (user_sessions.session_id)
  "iss": "public-api-skeleton",  // Issuer
  "aud": "skeleton-apps",        // Audience
  "iat": 1704067200,             // Issued At
  "exp": 1704070800,             // Expiration (1시간)
  "groups": ["USER", "ADMIN"],   // 그룹 목록
  "client_id": "web-app"         // 클라이언트 ID
}
```

> **Note**: 권한(permissions)은 JWT에 포함하지 않음.
> - 이유: 권한 목록이 길어질 수 있고, 그룹 권한 변경 시 JWT 재발급 없이 적용 가능
> - 권한은 Redis 캐시 또는 DB에서 조회

### 4.2 Refresh Token Payload

```json
{
  "sub": "12345",
  "jti": "session_abc123",
  "iss": "public-api-skeleton",
  "iat": 1704067200,
  "exp": 1704672000,             // Expiration (7일)
  "type": "refresh"
}
```

### 4.3 Token 검증 흐름

```
1. JWT 서명 검증 (RSA 또는 HS256)
2. JWT 만료 시간 검증
3. Redis 캐시에서 세션 조회:
   - Cache Hit → revoked 확인 → 권한 정보 사용
   - Cache Miss → DB 조회 → 캐시 저장
4. 세션 유효성 확인:
   - is_revoked = false
   - expires_at > now()
5. SecurityContext에 인증 정보 설정
```

---

## 5. API 엔드포인트 설계

### 5.1 인증 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/login` | 로그인 (ID/Password) |
| POST | `/auth/logout` | 로그아웃 |
| POST | `/auth/refresh` | 토큰 갱신 |
| GET | `/auth/oauth2/{provider}` | 소셜 로그인 시작 |
| GET | `/auth/oauth2/{provider}/callback` | 소셜 로그인 콜백 |
| POST | `/auth/password/reset` | 비밀번호 재설정 요청 |
| POST | `/auth/password/change` | 비밀번호 변경 |

### 5.2 사용자 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users/me` | 내 정보 조회 |
| PATCH | `/users/me` | 내 정보 수정 |
| GET | `/users/me/sessions` | 내 세션 목록 |
| DELETE | `/users/me/sessions/{sessionId}` | 특정 세션 종료 |
| DELETE | `/users/me/sessions` | 전체 세션 종료 (현재 제외) |
| GET | `/users/me/groups` | 내 그룹 목록 |
| GET | `/users/me/permissions` | 내 권한 목록 |

### 5.3 관리자 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/users` | 사용자 목록 |
| GET | `/admin/users/{id}` | 사용자 상세 |
| PATCH | `/admin/users/{id}/status` | 사용자 상태 변경 |
| DELETE | `/admin/users/{id}/sessions` | 사용자 세션 강제 종료 |
| GET | `/admin/groups` | 그룹 목록 |
| POST | `/admin/groups` | 그룹 생성 |
| PATCH | `/admin/groups/{id}` | 그룹 수정 |
| DELETE | `/admin/groups/{id}` | 그룹 삭제 |
| GET | `/admin/groups/{id}/permissions` | 그룹 권한 목록 |
| PUT | `/admin/groups/{id}/permissions` | 그룹 권한 설정 |
| POST | `/admin/users/{id}/groups` | 사용자에 그룹 할당 |
| DELETE | `/admin/users/{id}/groups/{groupId}` | 사용자에서 그룹 제거 |
| GET | `/admin/permissions` | 전체 권한 목록 |

---

## 6. 구현 단계

### Phase 1: 기본 인증 (MVP)
1. 프로젝트 세팅 (internal-api-skeleton 기반)
2. 사용자/세션 테이블 생성
3. JWT 발급/검증 구현
4. 로그인/로그아웃 API
5. Redis 세션 캐시 구현
6. Security Filter 구현

### Phase 2: 소셜 로그인
1. OAuth2 Client 설정 (Spring Security OAuth2)
2. 네이버 로그인 구현
3. 카카오 로그인 구현
4. 구글 로그인 구현
5. 소셜 계정 연동/해제

### Phase 3: 그룹 기반 권한 관리
1. 그룹/권한 테이블 생성
2. 기본 그룹/권한 데이터 초기화
3. 그룹 기반 접근 제어 (@PreAuthorize)
4. 권한 캐시 구현
5. 관리자 API 구현

### Phase 4: 확장 기능
1. 리소스 기반 권한 테이블
2. OAuth Authorization Server (제3자 앱)
3. 동시 접속 제어
4. 세션 관리 고도화

---

## 7. 기술 스택

| 영역 | 기술 |
|------|------|
| Framework | Spring Boot 3.x, Kotlin |
| Security | Spring Security 6.x, Spring OAuth2 Client |
| JWT | jjwt (io.jsonwebtoken) |
| DB | MySQL 8 (`skeleton_auth`) |
| Cache | Redis (세션 캐시, 권한 캐시) |
| Password | BCrypt |

---

## 8. 보안 고려사항

1. **비밀번호**: BCrypt 해시 (cost factor 12)
2. **Refresh Token**: DB 저장 시 해시값만 저장
3. **HTTPS**: 필수
4. **Rate Limiting**: 로그인 시도 제한
5. **세션 강제 종료**: Redis 캐시 즉시 삭제로 실시간 무효화
6. **비밀번호 변경 시**: 전체 세션 무효화 (DB + Redis)
7. **그룹 권한 변경 시**: 해당 그룹 사용자 권한 캐시 무효화

---

## 9. 테이블 관계도 (ERD)

```
┌─────────────┐       ┌──────────────────────┐
│   users     │       │ user_social_accounts │
├─────────────┤       ├──────────────────────┤
│ id (PK)     │◄──────│ user_id              │
│ login_id    │       │ provider             │
│ password    │       │ provider_id          │
│ email       │       └──────────────────────┘
│ status      │
└─────────────┘
      │
      │ 1:N
      ▼
┌─────────────────┐       ┌──────────────┐
│ user_sessions   │       │ user_groups  │
├─────────────────┤       ├──────────────┤
│ id (PK)         │       │ id (PK)      │
│ user_id         │       │ user_id      │◄─────┐
│ session_id (UQ) │       │ group_id     │──────┼───┐
│ is_revoked      │       └──────────────┘      │   │
└─────────────────┘                             │   │
                                                │   │
┌──────────────┐       ┌───────────────────┐    │   │
│   groups     │       │ group_permissions │    │   │
├──────────────┤       ├───────────────────┤    │   │
│ id (PK)      │◄──────│ group_id          │◄───┼───┘
│ code (UQ)    │       │ permission_id     │────┼───┐
│ name         │       └───────────────────┘    │   │
│ is_default   │                                │   │
└──────────────┘                                │   │
                                                │   │
┌───────────────┐                               │   │
│  permissions  │                               │   │
├───────────────┤                               │   │
│ id (PK)       │◄──────────────────────────────┼───┘
│ code (UQ)     │                               │
│ resource_type │                               │
│ action        │                               │
└───────────────┘                               │
                                                │
[향후 확장: 리소스 기반 권한]                    │
┌─────────────────┐    ┌─────────────────────────┐
│ resource_types  │    │ resource_group_perms    │
├─────────────────┤    ├─────────────────────────┤
│ id (PK)         │◄───│ resource_id             │
│ code (UQ)       │    │ group_id                │◄──┘
└─────────────────┘    │ permission_id           │
        │              └─────────────────────────┘
        │ 1:N
        ▼
┌───────────────┐
│  resources    │
├───────────────┤
│ id (PK)       │
│ resource_type │
│ resource_key  │
│ owner_user_id │
└───────────────┘
```
