-- V3__insert_e2e_test_data.sql
-- E2E/통합 테스트용 데이터 초기화
--
-- 이 스크립트는 dev/local 환경에서만 실행됩니다.
-- prod 환경에서는 flyway locations 설정에서 test 폴더를 제외합니다.

-- =====================================================
-- 1. 테스트 사용자 생성
-- =====================================================
-- 비밀번호: Test@1234! (BCrypt 해시)
-- 해시는 BCryptPasswordEncoder.encode("Test@1234!") 로 만들고 matches 로 확인한 값이다(2026-09-16). 바꾸면 E2ETestDataSetup.DEFAULT_TEST_PASSWORD 와 같이 바꾼다.
-- $2a$10$0LhCqDbi0Z8vxMyBR59n4uiTVMN9sCwovrpYrPFkZQlASPxQgEad.

-- 일반 사용자
INSERT INTO users (login_id, password_hash, email, name, status, created_at, updated_at)
SELECT 'e2e_test_user', '$2a$10$0LhCqDbi0Z8vxMyBR59n4uiTVMN9sCwovrpYrPFkZQlASPxQgEad.',
       'e2e_test_user@test.com', 'E2E 테스트 사용자', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'e2e_test_user');

-- 관리자
INSERT INTO users (login_id, password_hash, email, name, status, created_at, updated_at)
SELECT 'e2e_test_admin', '$2a$10$0LhCqDbi0Z8vxMyBR59n4uiTVMN9sCwovrpYrPFkZQlASPxQgEad.',
       'e2e_test_admin@test.com', 'E2E 테스트 관리자', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'e2e_test_admin');

-- 최고 관리자
INSERT INTO users (login_id, password_hash, email, name, status, created_at, updated_at)
SELECT 'e2e_test_super_admin', '$2a$10$0LhCqDbi0Z8vxMyBR59n4uiTVMN9sCwovrpYrPFkZQlASPxQgEad.',
       'e2e_test_super_admin@test.com', 'E2E 테스트 최고관리자', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'e2e_test_super_admin');

-- 소셜 로그인 테스트용 사용자
INSERT INTO users (login_id, password_hash, email, name, status, created_at, updated_at)
SELECT 'e2e_test_social', '$2a$10$0LhCqDbi0Z8vxMyBR59n4uiTVMN9sCwovrpYrPFkZQlASPxQgEad.',
       'e2e_test_social@test.com', 'E2E 소셜로그인 테스트', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'e2e_test_social');

-- =====================================================
-- 2. 사용자 역할 할당
-- =====================================================

-- 일반 사용자 -> USER 역할
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW()
FROM users u, roles r
WHERE u.login_id = 'e2e_test_user' AND r.code = 'USER'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 관리자 -> ADMIN 역할
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW()
FROM users u, roles r
WHERE u.login_id = 'e2e_test_admin' AND r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 최고 관리자 -> SUPER_ADMIN 역할
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW()
FROM users u, roles r
WHERE u.login_id = 'e2e_test_super_admin' AND r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 소셜 로그인 사용자 -> USER 역할
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW()
FROM users u, roles r
WHERE u.login_id = 'e2e_test_social' AND r.code = 'USER'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- =====================================================
-- 3. 소셜 계정 연동 (E2E 테스트용)
-- 개인 소셜 계정은 시드하지 않는다. 소셜 로그인 브라우저 테스트(-Pkotest.tags=browser)를 돌릴 때는
-- SocialAccountQueryTest(-Pkotest.tags=manual)로 provider-id 를 찾아 로컬 DB 에 직접 연동하거나,
-- 파생 프로젝트의 테스트 전용 마이그레이션에 user_social_accounts INSERT 를 추가한다.

-- =====================================================
-- 4. OAuth2 클라이언트 (E2E 테스트용)
-- =====================================================
-- client_secret: test-secret (BCrypt 해시)
-- 해시는 BCryptPasswordEncoder.encode("test-secret") 로 만들고 matches 로 확인한 값이다(2026-09-16) — 이전 값은 어느 비밀번호와도 맞지 않았다.
-- $2a$10$AcvTYYk3tKfpS04CgQ2xqu4sgcTTe2F57R59Gk//jRbxT0BWWfOJ.

-- 1st Party 클라이언트 (내부 신뢰 앱) - Authorization Code + Refresh Token
INSERT INTO oauth2_clients (
    client_id, client_secret, client_name, description,
    grant_types, redirect_uris, scopes,
    access_token_validity, refresh_token_validity,
    is_active, is_first_party, require_pkce
)
SELECT 'e2e-first-party-client',
       '$2a$10$AcvTYYk3tKfpS04CgQ2xqu4sgcTTe2F57R59Gk//jRbxT0BWWfOJ.',
       'E2E 1st Party Client',
       'E2E 테스트용 내부 신뢰 클라이언트',
       'authorization_code,refresh_token',
       'http://localhost:3000/callback,http://localhost:8080/demo/auth/callback',
       'openid,profile,email,offline_access',
       3600, 604800,
       1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients WHERE client_id = 'e2e-first-party-client');

-- 3rd Party 클라이언트 (외부 앱) - Authorization Code + Refresh Token
INSERT INTO oauth2_clients (
    client_id, client_secret, client_name, description,
    grant_types, redirect_uris, scopes,
    access_token_validity, refresh_token_validity,
    is_active, is_first_party, require_pkce
)
SELECT 'e2e-third-party-client',
       '$2a$10$AcvTYYk3tKfpS04CgQ2xqu4sgcTTe2F57R59Gk//jRbxT0BWWfOJ.',
       'E2E 3rd Party Client',
       'E2E 테스트용 외부 앱 클라이언트',
       'authorization_code,refresh_token',
       'http://localhost:4000/callback,https://example.com/oauth/callback',
       'openid,profile,email',
       1800, 86400,
       1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients WHERE client_id = 'e2e-third-party-client');

-- PKCE 필수 클라이언트 (SPA/모바일 앱용)
INSERT INTO oauth2_clients (
    client_id, client_secret, client_name, description,
    grant_types, redirect_uris, scopes,
    access_token_validity, refresh_token_validity,
    is_active, is_first_party, require_pkce
)
SELECT 'e2e-pkce-client',
       '$2a$10$AcvTYYk3tKfpS04CgQ2xqu4sgcTTe2F57R59Gk//jRbxT0BWWfOJ.',
       'E2E PKCE Client',
       'E2E 테스트용 PKCE 필수 클라이언트 (SPA/모바일)',
       'authorization_code,refresh_token',
       'http://localhost:5173/callback,myapp://oauth/callback',
       'openid,profile,email,offline_access',
       3600, 2592000,
       1, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients WHERE client_id = 'e2e-pkce-client');

-- 비활성 클라이언트 (에러 케이스 테스트용)
INSERT INTO oauth2_clients (
    client_id, client_secret, client_name, description,
    grant_types, redirect_uris, scopes,
    access_token_validity, refresh_token_validity,
    is_active, is_first_party, require_pkce
)
SELECT 'e2e-inactive-client',
       '$2a$10$AcvTYYk3tKfpS04CgQ2xqu4sgcTTe2F57R59Gk//jRbxT0BWWfOJ.',
       'E2E Inactive Client',
       'E2E 테스트용 비활성 클라이언트 (에러 케이스)',
       'authorization_code',
       'http://localhost:9999/callback',
       'openid',
       3600, 604800,
       0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients WHERE client_id = 'e2e-inactive-client');
