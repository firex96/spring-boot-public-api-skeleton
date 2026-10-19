-- V5__create_mfa_tables.sql
-- 2차 인증(TOTP) 등록과 1회용 복구 코드. FK 는 두지 않는다(업무 DDL 규약).

-- 사용자당 하나 — setup 이 PENDING 으로 만들고 첫 코드 확인(activate)이 ACTIVE 로 바꾼다
CREATE TABLE user_mfa_credentials (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT NOT NULL,
    totp_secret_encrypted  VARCHAR(255) NOT NULL,          -- AES-256-GCM(MfaSecretCipher), AAD = 사용자 ID
    status                 VARCHAR(20) NOT NULL,           -- PENDING / ACTIVE
    last_used_step         BIGINT NULL,                    -- 마지막으로 통과한 TOTP 구간 — 같은 구간 재사용 방지(조건부 UPDATE)
    activated_at           DATETIME(6) NULL,
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_user_mfa_credentials_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1회용 복구 코드 — SHA-256 해시만 저장, 사용하면 used_at(조건부 UPDATE)
CREATE TABLE user_mfa_recovery_codes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_user_mfa_recovery_codes_user_hash (user_id, code_hash) -- 코드는 40비트라 사용자 사이 해시는 겹칠 수 있다 — 사용자 안에서만 유일
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
