-- V2__insert_initial_data.sql
-- 운영 필수 기본 데이터 초기화
--
-- 역할, 권한, 역할-권한 매핑 등 시스템 운영에 필요한 기본 데이터

-- =====================================================
-- 1. 기본 역할 생성
-- =====================================================
INSERT INTO roles (code, name, description, is_system, is_default) VALUES
('SUPER_ADMIN', '최고 관리자', '시스템 전체 권한을 가진 최고 관리자', 1, 0),
('ADMIN', '관리자', '일반 관리 권한을 가진 관리자', 1, 0),
('USER', '사용자', '일반 사용자', 1, 1);

-- =====================================================
-- 2. 기본 권한 생성
-- =====================================================
INSERT INTO permissions (code, name, description, resource_type, action, is_system) VALUES
-- 사용자 관련 권한
('user:read', '사용자 정보 조회', '자신의 사용자 정보를 조회할 수 있는 권한', 'USER', 'READ', 1),
('user:write', '사용자 정보 수정', '자신의 사용자 정보를 수정할 수 있는 권한', 'USER', 'WRITE', 1),

-- 관리자 권한
('admin:user:read', '사용자 관리 조회', '모든 사용자 정보를 조회할 수 있는 권한', 'USER', 'READ', 1),
('admin:user:write', '사용자 관리 수정', '모든 사용자 정보를 수정할 수 있는 권한', 'USER', 'WRITE', 1),
('admin:user:delete', '사용자 삭제', '사용자를 삭제할 수 있는 권한', 'USER', 'DELETE', 1),
('admin:group:manage', '그룹 관리', '그룹을 관리할 수 있는 권한', 'GROUP', 'MANAGE', 1),
('admin:permission:manage', '권한 관리', '권한을 관리할 수 있는 권한', 'PERMISSION', 'MANAGE', 1),

-- 시스템 권한
('admin:system:manage', '시스템 관리', '시스템 설정을 관리할 수 있는 권한', 'SYSTEM', 'MANAGE', 1),

-- OAuth2 관리 권한
('oauth2:manage', 'OAuth2 클라이언트 관리', 'OAuth2 클라이언트를 관리할 수 있는 권한', 'OAUTH2', 'MANAGE', 1),

-- 감사 로그 및 보안 관리 권한
('admin:audit:read', '감사 로그 조회', '감사 로그를 조회할 수 있는 권한', 'AUDIT', 'READ', 1),
('admin:security:manage', '보안 관리', 'IP 차단 및 계정 잠금을 관리할 수 있는 권한', 'SECURITY', 'MANAGE', 1);

-- =====================================================
-- 3. 역할별 권한 할당
-- =====================================================

-- USER 역할 권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'USER' AND p.code IN ('user:read', 'user:write');

-- ADMIN 역할 권한 (USER 권한 + 관리자 권한)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN (
    'user:read', 'user:write',
    'admin:user:read', 'admin:user:write',
    'admin:group:manage',
    'admin:audit:read'
);

-- SUPER_ADMIN 역할 권한 (모든 권한)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SUPER_ADMIN';
