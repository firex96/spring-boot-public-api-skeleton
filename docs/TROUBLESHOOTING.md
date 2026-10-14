# Troubleshooting Guide

> Public API Skeleton 자주 발생하는 문제 및 해결 방법

## 목차

- [빌드 및 실행 문제](#빌드-및-실행-문제)
- [인증 문제](#인증-문제)
- [OAuth2/OIDC 문제](#oauth2oidc-문제)
- [소셜 로그인 문제](#소셜-로그인-문제)
- [데이터베이스 문제](#데이터베이스-문제)
- [테스트 문제](#테스트-문제)
- [성능 문제](#성능-문제)
- [배포 문제](#배포-문제)

---

## 빌드 및 실행 문제

### Gradle 빌드 실패

#### 문제: Java 버전 불일치

**증상:**
```
> Task :compileKotlin FAILED
e: /path/to/file.kt: Unresolved reference: ...
```

**해결:**
```bash
# Java 버전 확인
java -version

# sdkman으로 Java 21 설치
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem

# Gradle이 올바른 JDK 사용 확인
./gradlew -version
```

#### 문제: 의존성 다운로드 실패

**증상:**
```
Could not resolve all dependencies for configuration ':compileClasspath'.
```

**해결:**
```bash
# Gradle 캐시 삭제
./gradlew clean
rm -rf ~/.gradle/caches

# 오프라인 모드 비활성화 확인
# gradle.properties에서 org.gradle.offline 제거

# 다시 빌드
./gradlew build --refresh-dependencies
```

#### 문제: ktlint 검사 실패

**증상:**
```
> Task :ktlintKotlinScriptCheck FAILED
/path/to/file.kt:10:1: Unexpected indentation
```

**해결:**
```bash
# 자동 포맷팅
./gradlew ktlintFormat

# 또는 IDE에서 ktlint 플러그인 사용
# IntelliJ: Settings > Tools > ktlint > Format on save
```

### 애플리케이션 시작 실패

#### 문제: 포트 충돌

**증상:**
```
Web server failed to start. Port 8080 was already in use.
```

**해결:**
```bash
# 사용 중인 포트 확인
lsof -i :8080

# 해당 프로세스 종료
kill -9 <PID>

# 또는 다른 포트로 실행
./gradlew bootRun --args='--server.port=8081'
```

#### 문제: 프로파일 설정 오류

**증상:**
```
Failed to configure a DataSource: 'url' attribute is not specified
```

**해결:**
```bash
# 프로파일 지정하여 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 환경 변수로 프로파일 설정
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

#### 문제: Bean 생성 실패

**증상:**
```
Error creating bean with name 'xxx': Unsatisfied dependency...
```

**해결:**
1. 순환 참조 확인
   ```kotlin
   // 순환 참조 해결 - @Lazy 사용
   @Service
   class ServiceA(
       @Lazy private val serviceB: ServiceB
   )
   ```

2. 누락된 Bean 확인
   ```kotlin
   // @Component, @Service, @Repository 어노테이션 확인
   @Service  // <- 누락되었는지 확인
   class MyService
   ```

3. 프로파일 조건 확인
   ```kotlin
   @Profile("local")  // <- 현재 활성 프로파일과 일치하는지 확인
   @Service
   class LocalOnlyService
   ```

---

## 인증 문제

### 401 Unauthorized 에러

#### 문제: 토큰 누락

**증상:**
```json
{
  "status": 401,
  "divisionCode": "G008",
  "resultMsg": "Authorization header missing"
}
```

**해결:**
```bash
# Authorization 헤더 추가
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

#### 문제: 토큰 만료

**증상:**
```json
{
  "status": 401,
  "divisionCode": "G008",
  "resultMsg": "Token has expired"
}
```

**해결:**
```bash
# Refresh Token으로 새 Access Token 발급
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "YOUR_REFRESH_TOKEN"}'
```

#### 문제: 잘못된 토큰 형식

**증상:**
```json
{
  "status": 401,
  "divisionCode": "G008",
  "resultMsg": "Invalid token format"
}
```

**해결:**
- `Bearer ` 접두사 포함 확인
- 토큰 앞뒤 공백 제거
- 완전한 토큰인지 확인 (복사 시 잘림 방지)

### 403 Forbidden 에러

#### 문제: 권한 부족

**증상:**
```json
{
  "status": 403,
  "divisionCode": "G008",
  "resultMsg": "Access denied"
}
```

**해결:**
1. 사용자 권한 확인
   ```sql
   SELECT g.name, p.name
   FROM user_groups ug
   JOIN groups g ON ug.group_id = g.id
   JOIN group_permissions gp ON g.id = gp.group_id
   JOIN permissions p ON gp.permission_id = p.id
   WHERE ug.user_id = ?;
   ```

2. 필요한 권한 부여
   ```sql
   INSERT INTO user_groups (user_id, group_id) VALUES (?, ?);
   ```

### 로그인 실패

#### 문제: 계정 잠금

**증상:**
```json
{
  "status": 423,
  "divisionCode": "AUTH001",
  "resultMsg": "계정이 30분간 잠겼습니다"
}
```

**해결:**
```sql
-- 계정 잠금 해제 (관리자 권한 필요)
UPDATE users SET
  account_locked = false,
  failed_login_attempts = 0,
  lockout_end_time = NULL
WHERE login_id = 'username';
```

#### 문제: IP 차단

**증상:**
```json
{
  "status": 429,
  "divisionCode": "AUTH002",
  "resultMsg": "IP가 일시적으로 차단되었습니다"
}
```

**해결:**
```sql
-- IP 차단 해제 (관리자 권한 필요)
DELETE FROM blocked_ips WHERE ip_address = '192.168.1.100';
```

---

## OAuth2/OIDC 문제

### Authorization Code Flow 실패

#### 문제: redirect_uri 불일치

**증상:**
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri가 일치하지 않습니다"
}
```

**해결:**
1. 등록된 redirect_uri 확인
   ```sql
   SELECT redirect_uri FROM oauth2_clients WHERE client_id = 'your-client-id';
   ```

2. 요청 URL과 정확히 일치하는지 확인
   - 프로토콜 (http vs https)
   - 도메인
   - 포트
   - 경로
   - 쿼리 파라미터 없음

#### 문제: Authorization Code 만료

**증상:**
```json
{
  "error": "invalid_grant",
  "error_description": "authorization code가 만료되었습니다"
}
```

**해결:**
- Authorization Code는 1회용이며 일반적으로 10분 내에 사용해야 함
- 새로운 Authorization Code 요청 필요

### Token 발급 실패

#### 문제: Client 인증 실패

**증상:**
```json
{
  "error": "invalid_client",
  "error_description": "클라이언트 인증에 실패했습니다"
}
```

**해결:**
```bash
# client_id와 client_secret 확인
curl -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=client_credentials" \
  -d "client_id=correct-client-id" \
  -d "client_secret=correct-client-secret"
```

#### 문제: 지원하지 않는 grant_type

**증상:**
```json
{
  "error": "unsupported_grant_type",
  "error_description": "지원하지 않는 grant type입니다"
}
```

**해결:**
```sql
-- 클라이언트의 허용된 grant type 확인
SELECT allowed_grant_types FROM oauth2_clients WHERE client_id = 'your-client-id';

-- 필요한 grant type 추가
UPDATE oauth2_clients
SET allowed_grant_types = 'authorization_code,refresh_token,client_credentials'
WHERE client_id = 'your-client-id';
```

### JWKS/Discovery 문제

#### 문제: JWKS 엔드포인트 오류

**증상:**
```
Failed to fetch JWKS from /.well-known/jwks.json
```

**해결:**
1. RSA 키 설정 확인
   ```yaml
   # application.yml — 논리 키 참조. 값은 환경변수(OIDC_RSA_PRIVATE_KEY 등)나 비밀 저장소가 채운다
   oidc:
     rsa-private-key: ${oidc-rsa-private-key}
     rsa-public-key: ${oidc-rsa-public-key}
   ```

2. JWKS 엔드포인트 테스트
   ```bash
   curl http://localhost:8080/.well-known/jwks.json
   ```

---

## 소셜 로그인 문제

### Google 로그인 실패

#### 문제: Invalid OAuth Client

**증상:**
```
Error 401: invalid_client
```

**해결:**
1. Google Cloud Console에서 OAuth 클라이언트 설정 확인
2. client_id와 client_secret이 올바른지 확인
3. 승인된 리디렉션 URI에 콜백 URL 등록 확인

```yaml
# application.yml — 논리 키 참조 (oauth2 주석을 푼다)
oauth2:
  redirect-base-url: ${oauth2-redirect-base-url}   # 예: https://api.example.com
  google:
    client-id: ${google-client-id}
    client-secret: ${google-client-secret}
```

### Kakao 로그인 실패

#### 문제: 동의 항목 미설정

**증상:**
```json
{
  "error": "KOE101",
  "error_description": "Invalid scope"
}
```

**해결:**
1. Kakao Developers 콘솔에서 동의 항목 활성화
   - 닉네임
   - 이메일 (선택)
   - 프로필 사진 (선택)

2. scope 설정 확인
   ```yaml
   social:
     kakao:
       scope: profile_nickname,account_email
   ```

### Naver 로그인 실패

#### 문제: 검수 미완료

**증상:**
네이버 로그인 화면에서 "검수가 완료되지 않은 애플리케이션" 메시지

**해결:**
1. 네이버 개발자 센터에서 검수 요청
2. 개발 환경에서는 테스트 계정 등록하여 사용

---

## 데이터베이스 문제

### 연결 실패

#### 문제: 연결 거부

**증상:**
```
Connection refused to host: <dev-db-host>:3306
```

`local` 프로필은 docker compose 로 띄운 MySQL 을 본다.

**해결:**
```bash
# 로컬 인프라가 떠 있는지 (local 프로필)
docker compose ps
docker compose up -d

# 운영 — DB 포트 도달 확인 (호스트는 논리 키 db-host / 환경변수 DB_HOST)
nc -vz <db-host> 3306

# 계정 — 논리 키 db-{reader,writer}-{username,password} 가 환경변수·비밀 저장소에 있는지 확인 (값은 출력하지 않는다)
```

스키마나 init 스크립트를 바꿨는데 반영되지 않으면 볼륨을 다시 만든다 — init 스크립트는 볼륨을 처음 만들 때만 돈다: `docker compose down -v && docker compose up -d`.

#### 문제: 연결 풀 고갈

**증상:**
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

**해결:**
```yaml
# application.yml - 풀 크기 조정
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 기본 10에서 증가
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

### 쿼리 오류

#### 문제: Deadlock 발생

**증상:**
```
Deadlock found when trying to get lock; try restarting transaction
```

**해결:**
1. 트랜잭션 순서 일관성 유지
2. 트랜잭션 범위 최소화
3. 인덱스 확인

```kotlin
// 재시도 로직 추가
@Retryable(
    value = [DeadlockLoserDataAccessException::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 100)
)
@Transactional
fun updateWithRetry(...)
```

#### 문제: N+1 쿼리

**증상:**
```
많은 수의 SELECT 쿼리 로그
```

**해결:**
```kotlin
// Fetch Join 사용
@Query("SELECT u FROM User u LEFT JOIN FETCH u.groups")
fun findAllWithGroups(): List<User>

// 또는 @EntityGraph 사용
@EntityGraph(attributePaths = ["groups"])
fun findById(id: Long): User?
```

---

## 테스트 문제

### 테스트 실행 실패

#### 문제: E2E 테스트 401 오류

**증상:**
```
E2E 테스트에서 모든 API 호출이 401 반환
```

**해결:**
```yaml
# src/e2eTest/resources/application.yml
spring:
  profiles:
    active: test  # TestSecurityConfig 활성화
```

#### 문제: Testcontainers 시작 실패

**증상:**
```
Could not find a valid Docker environment
```

**해결:**
```bash
# Docker 실행 확인
docker info

# Docker 데몬 시작
sudo systemctl start docker

# 또는 Docker Desktop 시작 (Mac/Windows)
```

#### 문제: 통합 테스트 롤백 안됨

**증상:**
```
테스트 데이터가 다른 테스트에 영향
```

**해결:**
```kotlin
@SpringBootTest
@Transactional  // 각 테스트 후 롤백
class MyIntegrationTest {

    @Test
    fun testSomething() {
        // 테스트 코드
    }
}
```

---

## 성능 문제

### 응답 지연

#### 문제: 느린 쿼리

**증상:**
```
API 응답 시간이 1초 이상
```

**진단:**
```yaml
# 쿼리 로깅 활성화
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
```

**해결:**
1. 인덱스 추가
   ```sql
   CREATE INDEX idx_users_login_id ON users(login_id);
   ```

2. 쿼리 최적화
   ```kotlin
   // 필요한 컬럼만 조회
   @Query("SELECT new UserDto(u.id, u.loginId, u.email) FROM User u WHERE u.id = :id")
   fun findUserDtoById(id: Long): UserDto?
   ```

#### 문제: 메모리 부족

**증상:**
```
java.lang.OutOfMemoryError: Java heap space
```

**해결:**
```bash
# JVM 힙 메모리 증가
java -Xmx2g -Xms1g -jar app.jar

# 또는 application.yml에서
JAVA_OPTS="-Xmx2g -Xms1g"
```

### 동시성 문제

#### 문제: 동시 수정 충돌

**증상:**
```
ObjectOptimisticLockingFailureException
```

**해결:**
```kotlin
// @Version 필드 확인
@Entity
class User {
    @Version
    var version: Long = 0
}

// 재시도 로직 추가
@Retryable(
    value = [OptimisticLockingFailureException::class],
    maxAttempts = 3
)
fun updateUser(...)
```

---

## 배포 문제

배포 방식(VM·컨테이너·Kubernetes)은 파생 프로젝트가 정한다. 비밀값 출처는 프로필로 고른다 — 환경변수만(프로필 없음) · `aws` · `azure` · `gcp`.

### 기동 시 설정값 미해석

#### 문제: 논리 키가 해석되지 않음

**증상:**
```
java.lang.IllegalStateException: 해석되지 않은 설정 플레이스홀더 — 환경변수·비밀 저장소에 논리 키가 있는지, 프로필·자격증명이 맞는지 확인한다:
jwt.secret -> ${jwt-secret}
```

`UnresolvedPlaceholderGuard` 가 환경 준비 단계에서 막는다. 설정값·비밀값 규약대로 **폴백이 없어 즉시 실패**한다 — Spring Boot 의 `@ConfigurationProperties` 바인더는 해석 불가 `${…}` 를 리터럴로 남겨 조용히 뜨게 하므로, 그 상태를 두지 않기 위한 장치다.

**해결:**
1. yml 의 논리 키 오타 확인 (`git grep -n 'jwt-secret' -- 'src/main/resources/*.yml'`)
2. 값이 있는지 — 환경변수(`JWT_SECRET`) 또는 활성 프로필의 비밀 저장소:
   - `aws`: `aws secretsmanager describe-secret --secret-id "$AWS_SECRET_ID"` 로 비밀이 있는지 보고, JSON 안의 키 이름은 콘솔에서 확인한다 (값은 출력하지 않는다)
   - `azure`: `az keyvault secret show --vault-name <vault> --name jwt-secret --query id -o tsv`
   - `gcp`: `gcloud secrets describe jwt-secret --project "$GCP_PROJECT_ID"` — `application-gcp.yml` 에 대응 줄이 있는지도 본다
3. 권한 — 실행 주체(인스턴스 역할·관리 ID·워크로드 ID)가 비밀 읽기 권한을 가졌는지
4. 프로필 — `SPRING_PROFILES_ACTIVE` 가 의도한 출처(`aws`·`azure`·`gcp`)인지

### 비밀 저장소 접근 실패

**증상:** 기동 초기에 `AccessDenied`·`Forbidden`·`PermissionDenied`·자격증명 체인 오류

**해결:**
1. 자격증명 — AWS 기본 체인 · `DefaultAzureCredential` · Application Default Credentials 가 실행 환경에서 잡히는지
2. 위치 — `AWS_SECRET_ID`(·리전) · `AZURE_KEYVAULT_ENDPOINT` · `GCP_PROJECT_ID` 값
3. 네트워크 — 프라이빗 엔드포인트·방화벽

---

## 로그 분석

### 유용한 로그 패턴

```bash
# 에러 로그만 확인
grep -E "ERROR|Exception" /var/log/app.log

# 특정 traceId로 필터링
grep "traceId=abc123" /var/log/app.log

# 최근 100개 로그 실시간 확인
tail -f -n 100 /var/log/app.log

# 특정 시간대 로그
awk '/2024-01-15 10:00:/,/2024-01-15 11:00:/' /var/log/app.log
```

### 로그 레벨 동적 변경

```bash
# Actuator로 로그 레벨 변경
curl -X POST http://localhost:8080/actuator/loggers/com.rex \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## 도움 요청

문제가 해결되지 않는 경우:

1. **로그 수집**
   ```bash
   # 애플리케이션 로그 (파일 로깅 — local 이외 프로필은 ./logs/application.log)
   tail -n 1000 /var/log/app/application.log > app.log

   # 서비스 상태·최근 이벤트
   systemctl status public-api > service.log
   journalctl -u public-api --since '1 hour ago' > events.log
   ```

2. **환경 정보 수집**
   - Java 버전
   - Spring Boot 버전
   - 프로파일 설정
   - 에러 스택트레이스

3. **재현 단계 문서화**
   - 정확한 요청 (cURL 명령어)
   - 예상 결과
   - 실제 결과

---

## 참고

- [Error Codes](ERROR_CODES.md) - 에러 코드 상세
- [API Reference](API_REFERENCE.md) - API 명세
