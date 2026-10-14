-- 스키마와 애플리케이션 계정을 만든다 (컨테이너 볼륨을 처음 만들 때만 실행된다).
-- 계정·비밀번호는 application-local.yml 의 논리 키 값과 짝이다. 로컬 전용 값이다.
--   skeleton_main : 업무 예제 스키마 — 테이블은 02-business-tables.sh 가 db/manual/create-main.sql 로 만든다
--   skeleton_auth : 인증·인가 스키마 — 테이블은 앱 기동 시 Flyway(skeleton_admin)가 만든다
CREATE DATABASE IF NOT EXISTS skeleton_main CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS skeleton_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'skeleton_reader'@'%' IDENTIFIED BY 'skeleton_reader';
CREATE USER IF NOT EXISTS 'skeleton_writer'@'%' IDENTIFIED BY 'skeleton_writer';
CREATE USER IF NOT EXISTS 'skeleton_admin'@'%' IDENTIFIED BY 'skeleton_admin';

GRANT SELECT ON skeleton_main.* TO 'skeleton_reader'@'%';
GRANT SELECT ON skeleton_auth.* TO 'skeleton_reader'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON skeleton_main.* TO 'skeleton_writer'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON skeleton_auth.* TO 'skeleton_writer'@'%';
-- Flyway 전용 — skeleton_auth 의 DDL·이력 테이블 관리
GRANT ALL PRIVILEGES ON skeleton_auth.* TO 'skeleton_admin'@'%';
FLUSH PRIVILEGES;
