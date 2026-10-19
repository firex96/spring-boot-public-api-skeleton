-- V4__add_auth_time.sql
-- 인가 코드에 사용자가 실제로 인증한 시각을 남긴다 — id_token 의 auth_time(OIDC Core §2)은 발급 시각이 아니라 이 값이다.
-- 원천은 브라우저 세션이 가리키는 user_sessions.created_at. 배포 전에 만든 코드는 NULL 이고, 그때는 발급 시각으로 대체한다.

ALTER TABLE oauth2_authorization_codes ADD COLUMN auth_time DATETIME(6) NULL;
