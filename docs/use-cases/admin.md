# Admin API 유즈케이스

## 개요

시스템 관리자를 위한 API입니다. 사용자, 그룹, 권한, 감사 로그, 보안 설정 등을 관리합니다.

## 인증 및 권한

모든 Admin API는 인증이 필요하며, 특정 역할(Role)이나 권한(Permission)이 필요합니다.

| 카테고리 | 필요 권한 |
|---|---|
| 사용자 관리 | ROLE_ADMIN 또는 ROLE_SUPER_ADMIN |
| 그룹 관리 (조회) | ROLE_ADMIN 또는 ROLE_SUPER_ADMIN |
| 그룹 관리 (생성/수정/삭제) | ROLE_SUPER_ADMIN |
| 권한 관리 | ROLE_SUPER_ADMIN |
| 감사 로그 | admin:audit:read 권한 |
| 보안 관리 | admin:security:manage 권한 |

---

## 1. 사용자 관리 API

### UC-ADMIN-USER-001: 사용자의 그룹 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/users/{userId}/groups` |
| **설명** | 특정 사용자에게 할당된 그룹 목록을 조회합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

**응답 예시:**
```json
[
  { "id": 1, "code": "USER", "name": "일반 사용자" },
  { "id": 2, "code": "ADMIN", "name": "관리자" }
]
```

### UC-ADMIN-USER-002: 사용자에게 그룹 할당

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/users/{userId}/groups` |
| **설명** | 사용자에게 그룹을 추가로 할당합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

**요청:**
```json
{
  "groupIds": [1, 2, 3],
  "expiresAt": "2024-12-31T23:59:59"
}
```

### UC-ADMIN-USER-003: 사용자의 그룹 동기화

| 항목 | 내용 |
|---|---|
| **Endpoint** | `PUT /admin/users/{userId}/groups` |
| **설명** | 기존 그룹을 모두 제거하고 새 그룹 목록으로 교체합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

### UC-ADMIN-USER-004: 사용자에게서 그룹 제거

| 항목 | 내용 |
|---|---|
| **Endpoint** | `DELETE /admin/users/{userId}/groups/{groupId}` |
| **설명** | 사용자에게서 특정 그룹을 제거합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

### UC-ADMIN-USER-005: 사용자의 권한 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/users/{userId}/permissions` |
| **설명** | 사용자가 보유한 모든 권한(그룹을 통해 획득한 권한 포함)을 조회합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

---

## 2. 그룹 관리 API

### UC-ADMIN-GROUP-001: 전체 그룹 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/groups` |
| **설명** | 시스템에 등록된 모든 그룹을 조회합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

**응답 예시:**
```json
[
  {
    "id": 1,
    "code": "USER",
    "name": "일반 사용자",
    "description": "기본 사용자 그룹",
    "isDefault": true
  },
  {
    "id": 2,
    "code": "ADMIN",
    "name": "관리자",
    "description": "관리자 그룹",
    "isDefault": false
  }
]
```

### UC-ADMIN-GROUP-002: 그룹 상세 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/groups/{id}` |
| **설명** | 그룹의 상세 정보와 할당된 권한, 사용자 수를 조회합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

### UC-ADMIN-GROUP-003: 그룹 생성

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/groups` |
| **설명** | 새 그룹을 생성합니다. |
| **권한** | ROLE_SUPER_ADMIN |

**요청:**
```json
{
  "code": "MANAGER",
  "name": "매니저",
  "description": "매니저 그룹",
  "isDefault": false
}
```

### UC-ADMIN-GROUP-004: 그룹 수정

| 항목 | 내용 |
|---|---|
| **Endpoint** | `PUT /admin/groups/{id}` |
| **설명** | 그룹 정보를 수정합니다. |
| **권한** | ROLE_SUPER_ADMIN |

### UC-ADMIN-GROUP-005: 그룹 삭제

| 항목 | 내용 |
|---|---|
| **Endpoint** | `DELETE /admin/groups/{id}` |
| **설명** | 그룹을 삭제합니다. 사용자가 할당된 그룹은 삭제할 수 없습니다. |
| **권한** | ROLE_SUPER_ADMIN |

### UC-ADMIN-GROUP-006: 그룹에 권한 할당

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/groups/{id}/permissions` |
| **설명** | 그룹에 권한을 추가로 할당합니다. |
| **권한** | ROLE_SUPER_ADMIN |

### UC-ADMIN-GROUP-007: 그룹의 권한 동기화

| 항목 | 내용 |
|---|---|
| **Endpoint** | `PUT /admin/groups/{id}/permissions` |
| **설명** | 기존 권한을 모두 제거하고 새 권한 목록으로 교체합니다. |
| **권한** | ROLE_SUPER_ADMIN |

### UC-ADMIN-GROUP-008: 그룹에 사용자 할당

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/groups/{id}/users` |
| **설명** | 여러 사용자를 그룹에 한번에 할당합니다. |
| **권한** | ROLE_ADMIN, ROLE_SUPER_ADMIN |

---

## 3. 권한 관리 API

### UC-ADMIN-PERM-001: 전체 권한 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/permissions` |
| **설명** | 시스템에 등록된 모든 권한을 조회합니다. |
| **권한** | ROLE_SUPER_ADMIN |

**응답 예시:**
```json
[
  {
    "id": 1,
    "code": "admin:user:read",
    "name": "사용자 조회",
    "description": "사용자 정보 조회 권한",
    "resourceType": "user"
  }
]
```

### UC-ADMIN-PERM-002: 권한 생성

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/permissions` |
| **설명** | 새 권한을 생성합니다. |
| **권한** | ROLE_SUPER_ADMIN |

**요청:**
```json
{
  "code": "admin:reports:read",
  "name": "리포트 조회",
  "description": "리포트 조회 권한",
  "resourceType": "report"
}
```

### UC-ADMIN-PERM-003: 권한 수정

| 항목 | 내용 |
|---|---|
| **Endpoint** | `PUT /admin/permissions/{id}` |
| **설명** | 권한 정보를 수정합니다. |
| **권한** | ROLE_SUPER_ADMIN |

### UC-ADMIN-PERM-004: 권한 삭제

| 항목 | 내용 |
|---|---|
| **Endpoint** | `DELETE /admin/permissions/{id}` |
| **설명** | 권한을 삭제합니다. 그룹에 할당된 권한은 삭제할 수 없습니다. |
| **권한** | ROLE_SUPER_ADMIN |

---

## 4. 감사 로그 API

### UC-ADMIN-AUDIT-001: 감사 로그 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/audit/logs` |
| **설명** | 감사 로그를 페이지네이션하여 조회합니다. |
| **권한** | admin:audit:read |

**쿼리 파라미터:**
- `page` (기본값: 0)
- `size` (기본값: 20)
- `sort` (기본값: createdAt,desc)

**응답 예시:**
```json
{
  "content": [
    {
      "id": 1,
      "userId": 1,
      "eventType": "LOGIN",
      "eventCategory": "AUTH",
      "result": "SUCCESS",
      "ipAddress": "127.0.0.1",
      "createdAt": "2024-01-15T10:30:00"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0
}
```

### UC-ADMIN-AUDIT-002: 감사 로그 검색

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/audit/logs/search` |
| **설명** | 다양한 조건으로 감사 로그를 검색합니다. |
| **권한** | admin:audit:read |

**쿼리 파라미터:**
- `userId` - 사용자 ID
- `eventType` - 이벤트 타입 (LOGIN, LOGOUT, etc.)
- `eventCategory` - 이벤트 카테고리
- `result` - 결과 (SUCCESS, FAILURE)
- `startDate` - 시작 일시 (ISO 8601)
- `endDate` - 종료 일시 (ISO 8601)

### UC-ADMIN-AUDIT-003: 사용자별 감사 로그 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/audit/logs/user/{userId}` |
| **설명** | 특정 사용자의 감사 로그를 조회합니다. |
| **권한** | admin:audit:read |

### UC-ADMIN-AUDIT-004: 이벤트 타입별 감사 로그 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/audit/logs/event/{eventType}` |
| **설명** | 특정 이벤트 타입의 감사 로그를 조회합니다. |
| **권한** | admin:audit:read |

---

## 5. 보안 관리 API

### UC-ADMIN-SEC-001: 차단된 IP 목록 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/security/blocked-ips` |
| **설명** | 차단된 IP 주소 목록을 조회합니다. |
| **권한** | admin:security:manage |

### UC-ADMIN-SEC-002: IP 차단

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/security/blocked-ips` |
| **설명** | IP 주소를 수동으로 차단합니다. |
| **권한** | admin:security:manage |

**요청:**
```json
{
  "ipAddress": "192.168.1.100",
  "reason": "Suspicious activity",
  "expiresAt": "2024-12-31T23:59:59"
}
```

### UC-ADMIN-SEC-003: IP 차단 해제

| 항목 | 내용 |
|---|---|
| **Endpoint** | `DELETE /admin/security/blocked-ips/{id}` |
| **설명** | 차단된 IP를 해제합니다. |
| **권한** | admin:security:manage |

### UC-ADMIN-SEC-004: IP 차단 상태 확인

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/security/blocked-ips/check/{ipAddress}` |
| **설명** | 특정 IP가 차단되어 있는지 확인합니다. |
| **권한** | admin:security:manage |

**응답 예시:**
```json
{
  "ipAddress": "192.168.1.100",
  "isBlocked": true,
  "blockedAt": "2024-01-10T08:00:00",
  "reason": "Brute force attack",
  "expiresAt": "2024-01-17T08:00:00"
}
```

### UC-ADMIN-SEC-005: 계정 잠금 해제

| 항목 | 내용 |
|---|---|
| **Endpoint** | `POST /admin/security/accounts/unlock` |
| **설명** | 잠긴 계정을 수동으로 해제합니다. |
| **권한** | admin:security:manage |

**요청:**
```json
{
  "username": "locked_user"
}
```

### UC-ADMIN-SEC-006: 계정 상태 확인

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/security/accounts/status/{username}` |
| **설명** | 계정의 로그인 시도 상태와 잠금 여부를 확인합니다. |
| **권한** | admin:security:manage |

**응답 예시:**
```json
{
  "username": "testuser",
  "failedAttempts": 3,
  "isLocked": false,
  "lastFailedAttempt": "2024-01-15T10:00:00",
  "lockExpiresAt": null
}
```

### UC-ADMIN-SEC-007: 보안 설정 조회

| 항목 | 내용 |
|---|---|
| **Endpoint** | `GET /admin/security/settings` |
| **설명** | 현재 보안 설정 값을 조회합니다. |
| **권한** | admin:security:manage |

**응답 예시:**
```json
{
  "maxLoginAttempts": 5,
  "loginAttemptWindowMinutes": 15,
  "accountLockDurationMinutes": 30,
  "maxIpLoginAttempts": 10,
  "ipBlockDurationMinutes": 60,
  "bruteForceProtectionEnabled": true,
  "ipBlockingEnabled": true
}
```

---

## 테스트 시나리오 요약

### 사용자 관리
| 시나리오 | 예상 결과 |
|---|---|
| ADMIN 권한으로 사용자 그룹 조회 | 200 OK |
| USER 권한으로 사용자 그룹 조회 | 403 Forbidden |
| 존재하지 않는 사용자 조회 | 404 Not Found |

### 그룹 관리
| 시나리오 | 예상 결과 |
|---|---|
| SUPER_ADMIN으로 그룹 생성 | 201 Created |
| ADMIN으로 그룹 생성 시도 | 403 Forbidden |
| 중복 코드로 그룹 생성 | 409 Conflict |
| 사용자가 있는 그룹 삭제 | 400 Bad Request |

### 권한 관리
| 시나리오 | 예상 결과 |
|---|---|
| SUPER_ADMIN으로 권한 생성 | 201 Created |
| 중복 코드로 권한 생성 | 409 Conflict |
| 그룹에 할당된 권한 삭제 | 400 Bad Request |

### 감사 로그
| 시나리오 | 예상 결과 |
|---|---|
| 유효한 권한으로 로그 조회 | 200 OK |
| 날짜 범위로 검색 | 200 OK, 필터링된 결과 |
| 권한 없이 조회 | 403 Forbidden |

### 보안 관리
| 시나리오 | 예상 결과 |
|---|---|
| IP 차단 | 201 Created |
| 이미 차단된 IP 재차단 | 409 Conflict |
| 잠긴 계정 해제 | 200 OK |

---

## 에러 코드

| HTTP Status | 에러 코드 | 설명 |
|---|---|---|
| 400 | BAD_REQUEST | 입력 데이터 유효성 검증 실패 |
| 400 | CANNOT_DELETE | 삭제 불가 (참조 데이터 존재) |
| 401 | UNAUTHORIZED | 인증 필요 |
| 403 | FORBIDDEN | 권한 없음 |
| 404 | NOT_FOUND | 리소스 없음 |
| 409 | DUPLICATE | 중복 데이터 |
