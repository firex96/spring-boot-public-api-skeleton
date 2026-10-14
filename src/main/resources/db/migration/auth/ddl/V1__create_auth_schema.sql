-- V1__create_auth_schema.sql
-- 인증/인가 관련 전체 스키마 생성
--
-- 테이블 접두사: skeleton_
-- 다른 스키마와의 충돌 방지를 위해 모든 테이블에 접두사 사용

-- =====================================================
-- 1. 사용자 관련 테이블
-- =====================================================

-- 사용자 기본 정보
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NULL,
    email           VARCHAR(255) NULL,
    phone           VARCHAR(20) NULL,
    name            VARCHAR(100) NULL,
    nickname        VARCHAR(100) NULL,
    profile_image   VARCHAR(500) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   DATETIME(6) NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100) NULL,
    updated_by      VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_users_login_id ON users(login_id);
CREATE INDEX IX_users_email ON users(email);
CREATE INDEX IX_users_status ON users(status);

-- 소셜 계정 연동 정보
CREATE TABLE user_social_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    provider        VARCHAR(20) NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NULL,
    name            VARCHAR(100) NULL,
    profile_image   VARCHAR(500) NULL,
    access_token    VARCHAR(1000) NULL,
    refresh_token   VARCHAR(1000) NULL,
    token_expires_at DATETIME(6) NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_user_social_accounts_user_id ON user_social_accounts(user_id);
CREATE UNIQUE INDEX UX_user_social_accounts_provider ON user_social_accounts(provider, provider_id);

-- 사용자 세션 (JWT 메타정보 + 세션 관리)
CREATE TABLE user_sessions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    session_id      VARCHAR(64) NOT NULL,
    device_type     VARCHAR(20) NULL,
    device_info     VARCHAR(500) NULL,
    ip_address      VARCHAR(45) NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    access_token_issued_at DATETIME(6) NOT NULL,
    refresh_token_issued_at DATETIME(6) NOT NULL,
    access_token_expires_at DATETIME(6) NOT NULL,
    refresh_token_expires_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    is_revoked      TINYINT(1) NOT NULL DEFAULT 0,
    revoked_at      DATETIME(6) NULL,
    revoked_reason  VARCHAR(100) NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_user_sessions_user_id ON user_sessions(user_id);
CREATE UNIQUE INDEX UX_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX IX_user_sessions_refresh_expires ON user_sessions(refresh_token_expires_at);

-- =====================================================
-- 2. 역할/권한 관련 테이블
-- =====================================================

-- 역할 정의 (구 groups 테이블)
CREATE TABLE roles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NULL,
    is_system       TINYINT(1) NOT NULL DEFAULT 0,
    is_default      TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_roles_code ON roles(code);

-- 사용자-역할 매핑 (구 user_groups 테이블)
CREATE TABLE user_roles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    assigned_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by     BIGINT NULL,
    expires_at      DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IX_user_roles_role_id ON user_roles(role_id);
CREATE UNIQUE INDEX UX_user_roles ON user_roles(user_id, role_id);

-- 권한 정의
CREATE TABLE permissions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NULL,
    resource_type   VARCHAR(50) NULL,
    action          VARCHAR(50) NULL,
    is_system       TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_permissions_code ON permissions(code);
CREATE INDEX IX_permissions_resource_type ON permissions(resource_type);

-- 역할-권한 매핑 (구 group_permissions 테이블)
CREATE TABLE role_permissions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    granted_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    granted_by      BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IX_role_permissions_permission_id ON role_permissions(permission_id);
CREATE UNIQUE INDEX UX_role_permissions ON role_permissions(role_id, permission_id);

-- =====================================================
-- 3. OAuth2 Authorization Server 관련 테이블
-- =====================================================

-- OAuth2 클라이언트 등록 정보
CREATE TABLE oauth2_clients (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id               VARCHAR(64) NOT NULL,
    client_secret           VARCHAR(255) NOT NULL,
    client_name             VARCHAR(100) NOT NULL,
    description             VARCHAR(500) NULL,
    grant_types             VARCHAR(200) NOT NULL DEFAULT 'authorization_code,refresh_token',
    redirect_uris           VARCHAR(1000) NULL,
    scopes                  VARCHAR(500) NOT NULL DEFAULT 'openid,profile,email',
    access_token_validity   INT NOT NULL DEFAULT 3600,
    refresh_token_validity  INT NOT NULL DEFAULT 604800,
    is_active               TINYINT(1) NOT NULL DEFAULT 1,
    is_first_party          TINYINT(1) NOT NULL DEFAULT 0,
    require_pkce            TINYINT(1) NOT NULL DEFAULT 0,
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_oauth2_clients_client_id ON oauth2_clients(client_id);
CREATE INDEX IX_oauth2_clients_is_active ON oauth2_clients(is_active);

-- OAuth2 Authorization Code (임시 인증 코드)
CREATE TABLE oauth2_authorization_codes (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL,
    client_id               VARCHAR(64) NOT NULL,
    user_id                 BIGINT NOT NULL,
    redirect_uri            VARCHAR(500) NOT NULL,
    scopes                  VARCHAR(500) NULL,
    code_challenge          VARCHAR(128) NULL,
    code_challenge_method   VARCHAR(10) NULL,
    state                   VARCHAR(255) NULL,
    nonce                   VARCHAR(255) NULL,
    expires_at              DATETIME(6) NOT NULL,
    is_used                 TINYINT(1) NOT NULL DEFAULT 0,
    used_at                 DATETIME(6) NULL,
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_skeleton_oauth2_auth_codes_code ON oauth2_authorization_codes(code);
CREATE INDEX IX_skeleton_oauth2_auth_codes_client_id ON oauth2_authorization_codes(client_id);
CREATE INDEX IX_skeleton_oauth2_auth_codes_expires_at ON oauth2_authorization_codes(expires_at);

-- OAuth2 Access Token — 컬럼은 OAuth2AccessTokenEntity 가 정본(JWT token_id 만 저장, 해시 컬럼 없음)
CREATE TABLE oauth2_access_tokens (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id                VARCHAR(64) NOT NULL,
    client_id               VARCHAR(64) NOT NULL,
    user_id                 BIGINT NULL,
    scopes                  VARCHAR(500) NULL,
    token_type              VARCHAR(20) NOT NULL DEFAULT 'Bearer',
    issued_at               DATETIME(6) NOT NULL,
    expires_at              DATETIME(6) NOT NULL,
    is_revoked              TINYINT(1) NOT NULL DEFAULT 0,
    revoked_at              DATETIME(6) NULL,
    refresh_token_id        VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_oauth2_access_tokens_token_id ON oauth2_access_tokens(token_id);
CREATE INDEX IX_oauth2_access_tokens_refresh_token_id ON oauth2_access_tokens(refresh_token_id);
CREATE INDEX IX_oauth2_access_tokens_client_id ON oauth2_access_tokens(client_id);
CREATE INDEX IX_oauth2_access_tokens_user_id ON oauth2_access_tokens(user_id);
CREATE INDEX IX_oauth2_access_tokens_expires_at ON oauth2_access_tokens(expires_at);

-- OAuth2 Refresh Token — 컬럼은 OAuth2RefreshTokenEntity 가 정본(JWT token_id 만 저장, 해시·access_token_id 컬럼 없음)
CREATE TABLE oauth2_refresh_tokens (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id                VARCHAR(64) NOT NULL,
    client_id               VARCHAR(64) NOT NULL,
    user_id                 BIGINT NULL,
    scopes                  VARCHAR(500) NULL,
    issued_at               DATETIME(6) NOT NULL,
    expires_at              DATETIME(6) NOT NULL,
    is_revoked              TINYINT(1) NOT NULL DEFAULT 0,
    revoked_at              DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_oauth2_refresh_tokens_token_id ON oauth2_refresh_tokens(token_id);
CREATE INDEX IX_oauth2_refresh_tokens_client_id ON oauth2_refresh_tokens(client_id);
CREATE INDEX IX_oauth2_refresh_tokens_user_id ON oauth2_refresh_tokens(user_id);
CREATE INDEX IX_oauth2_refresh_tokens_expires_at ON oauth2_refresh_tokens(expires_at);

-- =====================================================
-- 4. 감사/보안 관련 테이블
-- =====================================================

-- 감사 로그 테이블
CREATE TABLE audit_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type          VARCHAR(50) NOT NULL,
    event_category      VARCHAR(50) NOT NULL,
    user_id             BIGINT NULL,
    username            VARCHAR(100) NULL,
    ip_address          VARCHAR(45) NULL,
    user_agent          VARCHAR(500) NULL,
    resource_type       VARCHAR(50) NULL,
    resource_id         VARCHAR(100) NULL,
    action              VARCHAR(50) NULL,
    description         VARCHAR(1000) NULL,
    old_value           TEXT NULL,
    new_value           TEXT NULL,
    result              VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    failure_reason      VARCHAR(500) NULL,
    session_id          VARCHAR(64) NULL,
    client_id           VARCHAR(64) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IX_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX IX_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IX_audit_logs_resource_type ON audit_logs(resource_type);

-- 차단된 IP 테이블
CREATE TABLE blocked_ips (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address          VARCHAR(45) NOT NULL,
    block_reason        VARCHAR(50) NOT NULL,
    description         VARCHAR(500) NULL,
    blocked_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at          DATETIME(6) NULL,
    blocked_by          BIGINT NULL,
    is_active           TINYINT(1) NOT NULL DEFAULT 1,
    unblocked_at        DATETIME(6) NULL,
    unblocked_by        BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX IX_blocked_ips_ip_address ON blocked_ips(ip_address);
CREATE INDEX IX_blocked_ips_expires_at ON blocked_ips(expires_at);

-- =====================================================
-- 5. 사용자 셀프 서비스 관련 테이블
-- =====================================================

-- 비밀번호 재설정 토큰 테이블
CREATE TABLE password_reset_tokens (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    token_hash          VARCHAR(64) NOT NULL,
    expires_at          DATETIME(6) NOT NULL,
    is_used             TINYINT(1) NOT NULL DEFAULT 0,
    used_at             DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_password_reset_tokens_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX IX_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX IX_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

-- 이메일 인증 토큰 테이블
CREATE TABLE email_verification_tokens (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    email               VARCHAR(255) NOT NULL,
    token_hash          VARCHAR(64) NOT NULL,
    expires_at          DATETIME(6) NOT NULL,
    is_verified         TINYINT(1) NOT NULL DEFAULT 0,
    verified_at         DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX UX_email_verify_tokens_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX IX_email_verify_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX IX_email_verify_tokens_expires_at ON email_verification_tokens(expires_at);
