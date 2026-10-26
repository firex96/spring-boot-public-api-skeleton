# Security Checklist

> Public API Skeleton 보안 설정 체크리스트 및 모범 사례

## 목차

- [배포 전 체크리스트](#배포-전-체크리스트)
- [인증 보안](#인증-보안)
- [API 보안](#api-보안)
- [데이터 보안](#데이터-보안)
- [인프라 보안](#인프라-보안)
- [모니터링 및 감사](#모니터링-및-감사)
- [보안 모범 사례](#보안-모범-사례)

---

## 배포 전 체크리스트

### 필수 체크 항목

| 항목 | 설명 | 체크 |
|------|------|------|
| RSA 키 설정 | 프로덕션 환경에서 RSA 키쌍 필수 설정 | [ ] |
| 비밀값 외부화 | 모든 비밀을 논리 키로 참조하고 값은 환경변수·비밀 저장소(aws·azure·gcp 프로필)에 둔다 | [ ] |
| HTTPS 강제 | HTTP 요청을 HTTPS로 리다이렉트 | [ ] |
| 신뢰 프록시 설정 | `security.trusted-proxies` 설정 | [ ] |
| CORS 설정 | 허용된 도메인만 CORS 허용 | [ ] |
| Rate Limiting | API 요청 제한 설정 | [ ] |
| 로그 레벨 | 프로덕션에서 민감 정보 로깅 비활성화 | [ ] |
| 에러 메시지 | 상세 에러 메시지 비활성화 | [ ] |
| 디버그 모드 | 디버그 모드 비활성화 확인 | [ ] |
| 테스트 계정 | 테스트 계정/데이터 제거 | [ ] |

### 설정 검증

```bash
# 운영 비밀값 출처 확인 — 활성 프로필(SPRING_PROFILES_ACTIVE)과 비밀 저장소에 RSA 키가 있는지 (값은 출력하지 않는다)
az keyvault secret show --vault-name <vault> --name oidc-rsa-private-key --query id -o tsv   # azure 예
aws secretsmanager describe-secret --secret-id "$AWS_SECRET_ID"                            # aws 예
gcloud secrets describe oidc-rsa-private-key --project "$GCP_PROJECT_ID"                   # gcp 예

# yml 에 평문 자격증명이 없는지 — 비밀은 기본값 없는 논리 키 참조만 (설정값·비밀값 규약)
git grep -nE '(password|secret|key):\s*[^$\s#]' -- 'src/main/resources/application.yml' 'src/main/resources/application-[ag]*.yml'   # 빈 출력이어야 한다

# 인증서·개인키가 저장소에 없는지 확인
git ls-files | grep -iE '\.(pem|crt|key|p7b|pfx)$'   # 빈 출력이어야 한다
```

---

## 인증 보안

### JWT 토큰 보안

#### 필수 설정

```yaml
# application.yml (oidc 주석을 푼다)
oidc:
  require-rsa-key: true  # RSA 키 필수
  rsa-private-key: ${oidc-rsa-private-key}   # 논리 키 — 값은 환경변수·비밀 저장소에
  rsa-public-key: ${oidc-rsa-public-key}
  access-token-validity-seconds: 3600      # 1시간
  refresh-token-validity-seconds: 2592000  # 30일

jwt:
  issuer: https://api.example.com
  audience: public-api
```

#### RSA 키 생성

```bash
# RSA 2048비트 키 생성 (최소 권장)
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# RSA 4096비트 키 생성 (권장)
openssl genrsa -out private.pem 4096
openssl rsa -in private.pem -pubout -out public.pem

# 비밀 저장소에 논리 키 이름으로 등록한다 — yml 에 값을 넣지 않는다 (설정값·비밀값 규약). azure 예:
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.pem -out private_pkcs8.pem
az keyvault secret set --vault-name <vault> --name oidc-rsa-private-key --file private_pkcs8.pem --query id -o tsv
az keyvault secret set --vault-name <vault> --name oidc-rsa-public-key  --file public.pem        --query id -o tsv
# aws: JSON 비밀에 "oidc-rsa-private-key"·"oidc-rsa-public-key" 키로 추가 / gcp: gcloud secrets create oidc-rsa-private-key --data-file=private_pkcs8.pem
rm -P private.pem private_pkcs8.pem   # 로컬 사본은 남기지 않는다
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| RSA 알고리즘 사용 | HS256 대신 RS256 사용 | [ ] |
| 키 길이 | 최소 2048비트, 권장 4096비트 | [ ] |
| 토큰 만료 시간 | Access Token 1시간 이하 | [ ] |
| 토큰 해시 저장 | 원본 토큰 저장 금지 | [ ] |
| JTI 클레임 | 토큰 고유 식별자 포함 | [ ] |
| 민감 정보 제외 | 토큰에 비밀번호 등 미포함 | [ ] |

### 비밀번호 보안

#### 비밀번호 정책

```kotlin
// PasswordPolicyValidator.kt
class PasswordPolicyValidator {
    fun validate(password: String): ValidationResult {
        val errors = mutableListOf<String>()

        if (password.length < 8) {
            errors.add("비밀번호는 최소 8자 이상이어야 합니다")
        }
        if (!password.any { it.isUpperCase() }) {
            errors.add("대문자를 포함해야 합니다")
        }
        if (!password.any { it.isLowerCase() }) {
            errors.add("소문자를 포함해야 합니다")
        }
        if (!password.any { it.isDigit() }) {
            errors.add("숫자를 포함해야 합니다")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            errors.add("특수문자를 포함해야 합니다")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 비밀번호 해싱 | BCrypt 사용 (cost factor 12+) | [ ] |
| 비밀번호 정책 | 최소 8자, 복잡성 요구사항 | [ ] |
| 이전 비밀번호 제한 | 최근 N개 비밀번호 재사용 금지 | [ ] |
| 비밀번호 만료 | 90일마다 변경 요구 (선택) | [ ] |

### 로그인 보안

#### 로그인 시도 제한 설정

```yaml
# application.yml
security:
  login:
    max-attempts: 5              # 최대 시도 횟수
    lockout-duration-minutes: 30 # 잠금 기간
    ip-max-attempts: 20          # IP당 최대 시도
    ip-lockout-duration-minutes: 60
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 로그인 시도 제한 | 사용자/IP별 제한 | [ ] |
| 계정 잠금 | 일정 시도 후 자동 잠금 | [ ] |
| CAPTCHA | 반복 실패 시 CAPTCHA 요구 | [ ] |
| MFA 지원 | 이중 인증 옵션 제공 — TOTP(`/user/mfa`), 모든 로그인 경로에 2단계 | [x] |
| 로그인 알림 | 새 기기 로그인 시 알림 | [ ] |

### 세션 보안

| 항목 | 설명 | 체크 |
|------|------|------|
| 세션 타임아웃 | 적절한 세션 만료 시간 설정 | [ ] |
| 동시 세션 제한 | 최대 동시 세션 수 제한 | [ ] |
| 세션 고정 방지 | 로그인 시 세션 ID 재생성 | [ ] |
| 안전한 쿠키 | Secure, HttpOnly, SameSite 설정 | [ ] |

---

## API 보안

### 입력 검증

#### 요청 검증

```kotlin
// 요청 Model에 검증 어노테이션 적용
data class CreateUserRequest(
    @field:NotBlank(message = "로그인 ID는 필수입니다")
    @field:Size(min = 4, max = 50)
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$")
    val loginId: String,

    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,

    @field:Email
    val email: String,

    @field:Size(max = 100)
    val nickname: String,
)
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 입력 검증 | 모든 사용자 입력 검증 | [ ] |
| SQL Injection | 파라미터 바인딩 사용 | [ ] |
| XSS 방지 | 출력 인코딩, CSP 헤더 | [ ] |
| 경로 조작 방지 | 파일 경로 검증 | [ ] |
| 요청 크기 제한 | 최대 요청 본문 크기 설정 | [ ] |

### Rate Limiting

```yaml
# application.yml
rate-limit:
  enabled: true
  default-limit: 100        # 기본 분당 요청 수
  endpoints:
    "/auth/login": 10
    "/auth/signup": 5
    "/auth/refresh": 30
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 전역 Rate Limit | API 전체 요청 제한 | [ ] |
| 엔드포인트별 제한 | 민감 API 별도 제한 | [ ] |
| 사용자별 제한 | 인증된 사용자별 제한 | [ ] |
| IP별 제한 | IP 주소별 제한 | [ ] |
| 429 응답 | 제한 초과 시 적절한 응답 | [ ] |

### CORS 설정

```kotlin
// SecurityConfig.kt
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    val configuration = CorsConfiguration()
    configuration.allowedOrigins = listOf(
        "https://app.example.com",
        "https://admin.example.com"
    )
    configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    configuration.allowedHeaders = listOf("*")
    configuration.allowCredentials = true
    configuration.maxAge = 3600L

    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", configuration)
    return source
}
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 허용 출처 명시 | `*` 대신 명시적 도메인 | [ ] |
| 허용 메서드 제한 | 필요한 메서드만 허용 | [ ] |
| Credentials 설정 | 필요시만 true | [ ] |
| Preflight 캐싱 | maxAge 적절히 설정 | [ ] |

### 보안 헤더

```kotlin
// SecurityConfig.kt
http.headers { headers ->
    headers.contentSecurityPolicy { csp ->
        csp.policyDirectives("default-src 'self'; script-src 'self'")
    }
    headers.frameOptions { it.deny() }
    headers.httpStrictTransportSecurity { hsts ->
        hsts.includeSubDomains(true)
        hsts.maxAgeInSeconds(31536000)
    }
    headers.xssProtection { it.enable() }
    headers.contentTypeOptions { }
}
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| Content-Security-Policy | XSS 방지 | [ ] |
| X-Frame-Options | Clickjacking 방지 | [ ] |
| X-Content-Type-Options | MIME 스니핑 방지 | [ ] |
| Strict-Transport-Security | HTTPS 강제 | [ ] |
| X-XSS-Protection | 브라우저 XSS 필터 | [ ] |

---

## 데이터 보안

### 민감 데이터 처리

#### 암호화 대상

| 데이터 유형 | 저장 시 | 전송 시 | 로깅 |
|------------|--------|--------|------|
| 비밀번호 | BCrypt 해시 | HTTPS | 절대 금지 |
| Access Token | 해시 저장 | HTTPS | 마스킹 |
| Refresh Token | 해시 저장 | HTTPS | 마스킹 |
| 개인정보 (이메일, 전화번호) | 암호화 권장 | HTTPS | 마스킹 |
| API Key | 해시 저장 | HTTPS | 절대 금지 |
| Client Secret | 해시 저장 | HTTPS | 절대 금지 |

#### 민감 데이터 마스킹

```kotlin
// LogMaskingUtil.kt
object LogMaskingUtil {
    fun maskToken(token: String?): String {
        if (token == null || token.length < 20) return "***"
        return "${token.take(10)}...${token.takeLast(5)}"
    }

    fun maskEmail(email: String?): String {
        if (email == null) return "***"
        val parts = email.split("@")
        if (parts.size != 2) return "***"
        val localPart = parts[0]
        val maskedLocal = if (localPart.length > 2) {
            "${localPart.take(2)}***"
        } else {
            "***"
        }
        return "$maskedLocal@${parts[1]}"
    }

    fun maskPhoneNumber(phone: String?): String {
        if (phone == null || phone.length < 4) return "***"
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 저장 시 암호화 | 민감 데이터 암호화 저장 | [ ] |
| 전송 시 암호화 | TLS 1.2 이상 사용 | [ ] |
| 로그 마스킹 | 민감 정보 로그 마스킹 | [ ] |
| 키 관리 | 암호화 키 안전한 저장 | [ ] |

### 데이터베이스 보안

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 최소 권한 원칙 | 필요한 권한만 부여 | [ ] |
| 네트워크 격리 | DB 서버 격리 | [ ] |
| 접근 로깅 | DB 접근 로그 기록 | [ ] |
| 백업 암호화 | 백업 파일 암호화 | [ ] |
| 연결 암호화 | SSL/TLS 연결 | [ ] |

---

## 인프라 보안

### 서버 보안

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| OS 패치 | 최신 보안 패치 적용 | [ ] |
| 불필요한 서비스 | 미사용 서비스 비활성화 | [ ] |
| 방화벽 | 필요한 포트만 개방 | [ ] |
| SSH 접근 | 키 기반 인증만 허용 | [ ] |
| 루트 로그인 | 루트 직접 로그인 금지 | [ ] |

### 실행 계정과 비밀값

| 항목 | 설명 | 체크 |
|------|------|------|
| 비루트 실행 | 전용 서비스 계정(컨테이너면 비루트 사용자)으로 실행 | [ ] |
| 워크로드 자격증명 | 인스턴스 역할·관리 ID·워크로드 ID 로 비밀 저장소 접근 — 장기 액세스 키를 서버·이미지에 두지 않는다 | [ ] |
| 비밀값 출처 | 비밀은 비밀 저장소(aws·azure·gcp 프로필) 또는 오케스트레이터 Secret 으로만 주입한다 | [ ] |
| yml 평문 없음 | 비밀은 기본값 없는 논리 키 참조만 — `local` 프로필의 docker compose 계정만 예외 | [ ] |
| 리소스 제한 | JVM 힙 상한(`-Xmx`) 명시 | [ ] |

### 신뢰할 수 있는 프록시 설정

X-Forwarded-For 헤더 스푸핑 방지:

```yaml
# application.yml (또는 환경변수 SECURITY_TRUSTEDPROXIES_0=…)
security:
  trusted-proxies:
    - "10.0.0.0/8"        # 내부 네트워크
    - "172.16.0.0/12"     # 사설 대역
    - "192.168.0.0/16"    # 로컬 네트워크
```

---

## 모니터링 및 감사

### 감사 로그

#### 기록 대상 이벤트

| 이벤트 | 설명 | 중요도 |
|--------|------|--------|
| LOGIN_SUCCESS | 로그인 성공 | 중간 |
| LOGIN_FAILURE | 로그인 실패 | 높음 |
| LOGOUT | 로그아웃 | 낮음 |
| PASSWORD_CHANGE | 비밀번호 변경 | 높음 |
| PASSWORD_RESET | 비밀번호 재설정 | 높음 |
| ACCOUNT_LOCKED | 계정 잠금 | 높음 |
| PERMISSION_DENIED | 권한 거부 | 높음 |
| SUSPICIOUS_ACTIVITY | 의심스러운 활동 | 높음 |
| TOKEN_REFRESH | 토큰 갱신 | 낮음 |
| DATA_EXPORT | 데이터 내보내기 | 높음 |

#### 감사 로그 형식

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "traceId": "abc123",
  "userId": 12345,
  "action": "LOGIN_FAILURE",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "details": {
    "reason": "invalid_password",
    "attemptCount": 3
  },
  "success": false
}
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 로그인 이벤트 | 성공/실패 모두 기록 | [ ] |
| 권한 변경 | 권한 부여/제거 기록 | [ ] |
| 데이터 접근 | 민감 데이터 접근 기록 | [ ] |
| 설정 변경 | 시스템 설정 변경 기록 | [ ] |
| 로그 보관 | 최소 90일 보관 | [ ] |
| 로그 변조 방지 | 로그 무결성 보장 | [ ] |

### 보안 알림

#### 알림 대상 이벤트

```yaml
# 알림 설정
alerts:
  suspicious-activity:
    - multiple-login-failures  # 5회 이상 로그인 실패
    - unusual-location-login   # 비정상 위치 로그인
    - concurrent-sessions      # 동시 세션 초과
    - admin-action-detected    # 관리자 작업
    - rate-limit-exceeded      # Rate limit 초과
```

#### 체크리스트

| 항목 | 설명 | 체크 |
|------|------|------|
| 실시간 알림 | 보안 이벤트 즉시 알림 | [ ] |
| 알림 채널 | 이메일, Slack, PagerDuty | [ ] |
| 에스컬레이션 | 심각도별 에스컬레이션 | [ ] |
| 오탐 관리 | 알림 튜닝 | [ ] |

---

## 보안 모범 사례

### 개발 단계

1. **코드 리뷰**
   - 보안 관점 코드 리뷰 필수
   - 민감 데이터 처리 코드 집중 검토

2. **정적 분석**
   ```bash
   # 의존성 취약점 검사
   ./gradlew dependencyCheckAnalyze
   ```

3. **비밀 정보 관리**
   - 코드에 하드코딩 금지
   - `.gitignore`에 민감 파일 추가
   - pre-commit hook으로 검사

### 배포 단계

1. **환경 분리**
   - 개발/스테이징/프로덕션 완전 분리
   - 프로덕션 데이터 개발 환경 사용 금지

2. **시크릿 로테이션**
   - 정기적인 키/토큰 교체
   - 자동화된 로테이션 권장

3. **배포 자동화**
   - CI/CD 파이프라인에 보안 검사 포함
   - 수동 배포 최소화

### 운영 단계

1. **정기 보안 점검**
   - 월간 취약점 스캔
   - 분기별 보안 감사

2. **인시던트 대응**
   - 대응 절차 문서화
   - 연락처 목록 유지
   - 정기 훈련

3. **패치 관리**
   - 보안 패치 신속 적용
   - 의존성 정기 업데이트

### 보안 도구

| 도구 | 용도 | 적용 단계 |
|------|------|----------|
| OWASP Dependency-Check | 의존성 취약점 검사 | CI/CD |
| SonarQube | 정적 코드 분석 | CI/CD |
| Trivy | 컨테이너 이미지 스캔 | CI/CD |
| OWASP ZAP | 동적 보안 테스트 | 스테이징 |
| Vault | 시크릿 관리 | 운영 |

---

## 비상 대응 체크리스트

### 보안 사고 발생 시

1. [ ] 영향 범위 파악
2. [ ] 증거 보존 (로그, 스냅샷)
3. [ ] 영향받은 시스템 격리
4. [ ] 관련자 통보
5. [ ] 근본 원인 분석
6. [ ] 복구 및 패치
7. [ ] 사후 분석 보고서

### 토큰 유출 시

1. [ ] 유출된 토큰 즉시 무효화
2. [ ] 관련 사용자 세션 종료
3. [ ] 비밀번호 재설정 요구
4. [ ] 감사 로그 분석
5. [ ] 영향 범위 파악

### 데이터 유출 시

1. [ ] 유출 경로 차단
2. [ ] 영향받은 데이터 파악
3. [ ] 법적 요구사항 확인 (GDPR 등)
4. [ ] 영향받은 사용자 통보
5. [ ] 규제 기관 신고 (필요시)

---

## 참고

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [Error Codes](ERROR_CODES.md) - 보안 관련 에러 코드
