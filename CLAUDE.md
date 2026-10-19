# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Spring Boot 기반 Public API 스켈레톤 프로젝트입니다.
- Kotlin 기반
- Multi-datasource 지원 (JPA + JDBC)
- JWT 기반 인증
- OAuth2/OIDC 지원
- Social Login (Google, Kakao, Naver)

## Build and Development Commands

### Build
```bash
# Build the project
./gradlew build

# Clean and build
./gradlew clean build

# Build without tests
./gradlew build -x test
```

### Run
```bash
# 로컬 인프라(MySQL·Redis) — 스키마·계정·main 업무 테이블까지 만든다
docker compose up -d

# Run the Spring Boot application (SPRING_PROFILES_ACTIVE 가 없으면 local — Flyway 가 skeleton_auth 를 만든다)
./gradlew bootRun

# 비밀값 출처 프로필로 실행 (aws · azure · gcp, 필요하면 ,loki)
SPRING_PROFILES_ACTIVE=aws AWS_SECRET_ID=… ./gradlew bootRun
```

### Code Quality
```bash
# Run ktlint checks
./gradlew ktlintCheck

# Auto-format code with ktlint
./gradlew ktlintFormat

# Run all checks (including tests and ktlint)
./gradlew check
```

### Testing

테스트는 3가지 유형으로 분리되어 있습니다:

| 유형 | 소스 경로 | 설명 | 속도 |
|------|----------|------|------|
| Unit Test | `src/test/unit/` | 순수 유닛 테스트 (MockK, Spring Context 없음) | 빠름 |
| Integration Test | `src/test/integration/` | 통합 테스트 (@SpringBootTest, DB 연동) | 중간 |
| E2E Test | `src/test/e2e/` | End-to-End 테스트 (실제 HTTP 요청) | 느림 |

```bash
# 유닛 테스트만 실행 (가장 빠름)
./gradlew test

# 통합 테스트만 실행
./gradlew integrationTest

# E2E 테스트만 실행 — docker compose up -d 선행, check 에 묶지 않는다. browser·manual 태그는 기본 제외
./gradlew e2eTest
./gradlew e2eTest -Pkotest.tags=browser   # Playwright 브라우저 e2e

# 전체 테스트 실행 (check = ktlint + test + integrationTest)
./gradlew check

# 특정 테스트 클래스 실행
./gradlew test --tests "com.rex.api.pub.skeleton.*TestClassName"
./gradlew integrationTest --tests "*ServiceTest"

# 테스트 상세 출력
./gradlew test --info
```

**테스트 유형별 역할:**

1. **Unit Test** (`src/test/unit/`)
   - MockK으로 의존성 Mock, Spring Context 로드 없음
   - 비즈니스 로직, 유틸리티, Mapper 테스트

2. **Integration Test** (`src/test/integration/`)
   - `@SpringBootTest`, `@MockkBean`, Testcontainers 사용
   - Repository CRUD, Service 통합, Controller MockMvc 테스트
   - DB 연동이 필요한 테스트는 여기서 수행
   - `integrationTest` 의 기본 프로필은 `testcontainers` — 보안을 끈 예외 처리 테스트는 `@ActiveProfiles("test", "testcontainers")`
   - 예: `ProductBizServiceTest`, `AwsSecretsManagerProfileIntegrationTest`(LocalStack)

3. **E2E Test** (`src/test/e2e/`)
   - `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate` 사용
   - 실제 HTTP 요청/응답 검증
   - docker compose 로 띄운 로컬 인프라에 `local,e2e` 프로필로 붙는다 (local 이 Flyway 로 e2e 시드를 넣는다)

## 레이어드 아키텍처 (필수 준수)

```
┌─────────────────────────────────────────────────────────────────┐
│                        Controller                                │
│  - HTTP 요청/응답 처리 — MappingService 만 주입                   │
│  - 응답은 Model, 요청 본문은 Dto (Entity 직접 사용 금지)          │
│  - 입력 검증 (@Valid) · 리다이렉트 URL 조립                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MappingService                              │
│  - DTO ↔ Model 변환 (MapStruct Mapper 사용)                      │
│  - 여러 BizService 조합하여 복합 응답 생성                        │
│  - 주입은 BizService · Mapper · Properties 만                     │
│  위치: service/mapping/                                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        BizService                                │
│  - 비즈니스 로직 처리                                            │
│  - 트랜잭션 경계 설정                                            │
│  - 여러 EntityService · Client(외부 API 어댑터) 조합             │
│  - DTO 객체 사용 — Repository 직접 주입 금지                      │
│  위치: service/biz/, service/auth/, service/audit/ 등            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EntityService                               │
│  - Entity ↔ DTO 변환 (MapStruct Mapper 사용) — Entity 는 여기까지 │
│  - Repository 호출 · 변경은 의도 메서드(revoke…, markUsed…)       │
│  - 캐싱 처리 (@Cacheable, @CacheEvict)                           │
│  - 단일 Entity에 대한 CRUD                                       │
│  위치: persistence/jpa/service/, persistence/jdbc/service/       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Repository                                │
│  - JPA Repository 또는 JDBC Template                             │
│  - 순수 데이터 접근만 담당                                        │
│  위치: persistence/jpa/repository/, persistence/jdbc/repository/ │
└─────────────────────────────────────────────────────────────────┘
```

### 계층별 책임 상세

#### Controller
```kotlin
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val mappingService: ProductMappingService, // MappingService 주입
) {
    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Int): ProductModel = // Model 반환
        mappingService.getProduct(id)
}
```

#### MappingService
```kotlin
@Service
class ProductMappingService(
    private val bizService: ProductBizService,
    private val mapper: ProductMapper, // MapStruct Mapper
) {
    fun getProduct(id: Long): ProductModel =
        (bizService.find(id) ?: throw NotFoundException("Product($id)")).let { mapper.toModel(it) } // DTO → Model — !! 대신 ?: 로 예외
}
```

#### BizService
```kotlin
@Service
@Transactional(readOnly = true, transactionManager = "mainTransactionManager") // 트랜잭션 매니저는 primary 라도 적는다
class ProductBizService(
    private val productEntityService: ProductJpaEntityService,
) {
    fun find(id: Int): ProductDto? = productEntityService.get(id) // DTO 사용

    @Transactional(transactionManager = "mainTransactionManager")
    fun add(productDto: ProductDto): ProductDto = productEntityService.create(productDto)
}
```

#### EntityService
```kotlin
@Service
@Transactional(readOnly = true, transactionManager = "mainTransactionManager")
class ProductJpaEntityService(
    private val repository: ProductJpaRepository,
    private val mapper: ProductMapper, // MapStruct Mapper
) {
    @CacheableNullSafe(value = ["ProductDto"], key = "#id")
    fun get(id: Int): ProductDto? =
        repository.findByIdOrNull(id)?.let { mapper.toDto(it) } // Entity → DTO — 매퍼는 non-null 시그니처, 부재는 ?.let 로

    @Transactional(transactionManager = "mainTransactionManager")
    fun create(dto: ProductDto): ProductDto =
        repository.save(mapper.toEntity(dto)).let { mapper.toDto(it) } // DTO → Entity → DTO
}
```

### MapStruct Mapper 규칙
```kotlin
// MapstructConfiguration: unmappedTargetPolicy = ERROR — 소스 없는 타깃은 ignore 명시(nullable·@Id 만), non-null 타깃은 매핑 명시,
// is-prefix Boolean 은 @Mapping(source = "x", target = "isX"). 매퍼 파라미터·반환은 non-null(nullability 만 다른 오버로드는 JVM 시그니처 충돌).
@Mapper(config = MapstructConfiguration::class)
interface ProductMapper {
    // Entity ↔ DTO (EntityService에서 사용)
    fun toDto(entity: ProductJpaEntity): ProductDto
    fun toEntity(dto: ProductDto): ProductJpaEntity
    fun toDtoList(entityList: List<ProductJpaEntity>): List<ProductDto>

    // DTO ↔ Model (MappingService에서 사용)
    fun toModel(dto: ProductDto): ProductModel
    fun toDto(model: ProductModel): ProductDto
    fun toModelList(dtoList: List<ProductDto>): List<ProductModel>
}
```

## 패키지 구조

```
com.rex.api.pub.skeleton/
├── client/                    # 외부 API 어댑터 (~Client, @Component) — BizService 아래
│   ├── social/              # Google · Kakao · Naver OAuth2 클라이언트
│   ├── core/                # Core API (@HttpExchange 인터페이스 + CoreApiClient)
│   └── example/             # ExampleWebClient
├── config/                    # 설정 클래스
│   ├── security/             # 보안 관련 Properties
│   └── persistence/          # 데이터소스 설정
├── controller/               # REST Controller
│   ├── auth/                # 인증 API
│   ├── oauth2/              # OAuth2 API
│   ├── admin/               # 관리자 API
│   └── ...
├── dto/                      # Data Transfer Object
│   ├── auth/                # 인증 DTO
│   ├── user/                # 사용자 DTO
│   ├── audit/               # 감사 DTO
│   └── oauth2/              # OAuth2 DTO
├── mapper/                   # MapStruct Mapper 인터페이스
├── model/                    # API 응답 Model
│   ├── auth/                # 인증·그룹·권한 Model
│   ├── oauth2/              # OAuth2 Model
│   ├── oidc/                # OIDC Model
│   ├── security/            # 보안 Model
│   └── user/                # 사용자 Model
├── service/
│   ├── mapping/             # MappingService (Controller 당 하나)
│   ├── biz/                 # BizService (CoreApi 등)
│   ├── auth/                # 인증 BizService
│   ├── audit/               # 감사 BizService
│   ├── user/                # 사용자 BizService
│   ├── oauth2/              # OAuth2 BizService
│   ├── oidc/                # OIDC BizService
│   └── security/            # 보안 BizService
├── persistence/
│   ├── jpa/
│   │   ├── entity/          # JPA Entity
│   │   │   ├── auth/       # 인증 Entity
│   │   │   ├── audit/      # 감사 Entity
│   │   │   └── security/   # 보안 Entity
│   │   ├── repository/      # JPA Repository
│   │   └── service/         # JPA EntityService (auth/ · oauth2/)
│   └── jdbc/
│       ├── entity/          # JDBC Entity
│       ├── repository/      # JDBC Repository
│       └── service/         # JDBC EntityService
├── exception/               # 커스텀 예외
├── filter/                  # HTTP 필터
├── handler/                 # 예외 핸들러
├── aspect/                  # AOP
└── util/                    # 유틸리티 클래스
```

## Key Technical Decisions

1. **Multi-Datasource Architecture**: MySQL 스키마 2개 동시 지원 (main @Primary — 업무 예제·수동 DDL, auth — 인증·인가·Flyway) — 각각 read/write 분리

2. **Dual Persistence Strategy**: JPA + JDBC Template 병행 사용
   - JPA: 표준 CRUD 작업
   - JDBC: 복잡한 쿼리

3. **MapStruct for Object Mapping**: Entity ↔ DTO ↔ Model 변환
   - 주의: Boolean 필드의 'is' prefix는 매핑 오류 발생 가능

3a. **로그인 보호는 `AuthBizService.login` 이 집행한다**: 차단 IP(`403`)·잠긴 계정(`423 AUTH001`) 거절 → 실패 `recordFailedAttempt`(임계 시 계정 잠금·IP 자동 차단) → 성공 `clearAttempts`. `@Transactional(noRollbackFor = …)` 이라 실패 기록이 예외 뒤에도 커밋된다. IP 를 모르면(null) IP 보호는 건너뛴다. 클라이언트 IP 는 컨트롤러가 `util.ClientIpResolver`(신뢰 프록시 CIDR 뒤에서만 `X-Forwarded-For` 인정, 아니면 `remoteAddr`)로 정한다 — 헤더를 직접 읽지 않는다(검출 `ClientIpResolverTest`). **모든 `@Transactional` 에 `transactionManager` 를 명시한다(primary 포함)** — auth 는 `authTransactionManager`, 빠뜨리면 primary(main)로 묵시 바인딩되어 `@Modifying` 예외·파생 `deleteBy…` 조용한 무효·커넥션 2개가 생긴다. 정본 conventions 「데이터베이스」 3, 검출 `TransactionManagerConventionTest`

4. **Kotlin Annotation Target**: `@JsonProperty` 등은 `@field:` 타겟 필요
   ```kotlin
   @field:JsonProperty("user_name")
   val userName: String
   ```

5. **Kotest for Testing**: JUnit 대신 Kotest + MockK 사용

6. **Secret Source Profiles**: 비밀값은 kebab-case 논리 키(`${jwt-secret}`)로만 참조한다. 환경변수 또는 `aws`(Secrets Manager) · `azure`(Key Vault) · `gcp`(Secret Manager) 프로필이 채운다. 세 클라우드 starter 는 `runtimeOnly` 이고 기본 `application.yml` 에서 꺼져 있다

7. **Virtual Threads**: Java 21 가상 스레드 활성화

8. **Flyway Database Migration**: DDL/DML 분리 관리
   - 자세한 내용은 아래 "Database Migration" 섹션 참조

## Database Migration

Flyway를 사용하여 데이터베이스 스키마와 데이터를 버전 관리합니다.

### 디렉토리 구조

```
db/migration/{schema}/
├── ddl/              # DDL: 테이블 생성, 수정 등 스키마 변경
│   ├── V1__create_tables.sql
│   └── V3__add_columns.sql
└── dml/
    ├── mandatory/    # 운영에 필수적인 기본 데이터 (역할, 권한 등)
    │   ├── V2__insert_initial_data.sql
    │   └── V4__insert_permissions.sql
    └── test/         # 개발/테스트 환경 전용 데이터
        └── V3__insert_e2e_test_data.sql
```

### 프로파일별 적용 범위

| 설정 | DDL | DML/mandatory | DML/test |
|---------|-----|---------------|----------|
| 기본 `application.yml` | ✅  | ✅            | ❌       |
| `local` (e2e 포함) | ✅  | ✅            | ✅       |

### 파일 명명 규칙

- `V{version}__{description}.sql` - 버전 마이그레이션 (예: `V1__create_auth_tables.sql`)
- `R__{description}.sql` - 반복 가능한 마이그레이션

### 설정 위치

- `FlywayConfiguration.kt` - 데이터소스별 Flyway 빈 설정
- `FlywayProperties.kt` - locations 설정 프로퍼티
- `application.yml` - 프로파일별 locations 설정

## 명명 규칙

### Package Naming
- `enums/` 사용 (`enum/` 금지 - Java 예약어로 MapStruct 빌드 실패)
- 예: `com.rex.api.pub.skeleton.enums.example.UserStatus`

### Class Naming
| 계층 | 접미사 | 예시 |
|------|--------|------|
| Controller | Controller | `UserController` |
| MappingService | MappingService | `UserSelfMappingService`, `AuthMappingService` |
| BizService | BizService | `UserBizService`, `AuthBizService` |
| EntityService | EntityService | `UserEntityService`, `UserSessionEntityService` |
| Client (외부 API 어댑터) | Client | `GoogleOAuth2Client`, `CoreApiClient` |
| Repository | Repository | `UserRepository` |
| Mapper | Mapper | `UserMapper` |
| DTO | Dto | `UserDto`, `UserProfileDto` |
| Model | Model | `UserModel`, `UserProfileModel` |
| Entity | Entity | `UserEntity` |
| Configuration | Configuration | `SecurityConfiguration`, `RedisConfiguration` |
| Enum | (접미사 없음) | `ProductStatus`, `UserStatus` |

### 공통 명명 규칙 (모든 스켈레톤 프로젝트 공통)

> **정본은 [`README.md`](README.md) 「코딩 규약」 절이다.** 명명 표·클래스 접미사·1클래스1파일·트랜잭션·String Enum 이 전부 거기 있다.
>
> 이 절에 표를 다시 두지 않는다 — 사본이 둘이 되면 조용히 어긋난다. 실제로 그런 상태였고(README 쪽이 CLAUDE.md 의 부분집합이었다) 2026-08-31 에 정리했다.
>
> 세 스켈레톤의 그 절은 **글자 단위로 동일**해야 한다. 규약을 고치려면 **세 레포 README 를 함께** 고친다.

### 메서드명

> 정본은 [.claude/rules/conventions.md](.claude/rules/conventions.md) 「명명」이다 — 단수 반환 `find`/`get`/`getBy…`/`findBy…`, 복수 반환 `findAll`/`findAllBy…`/`getAll`/`getAllBy…`/`list`.

| 작업 | BizService | EntityService |
|------|------------|---------------|
| 조회 | `find`, `get` | `get`, `findBy...` |
| 목록 | `findAll`, `list` | `getAll`, `findAllBy...` |
| 생성 | `add`, `create` | `create`, `save` |
| 수정 | `modify`, `update` | `update`, `save` |
| 삭제 | `remove`, `delete` | `delete` |

## Coding Guidelines

### 1. 1 Class per 1 File Principle
- 최상위 `class`·`interface`·`object`(enum·data·annotation 포함)는 파일에 하나만
- 예외: Kotlin extension 파일(최상위 함수·프로퍼티만), `sealed` 계층(sealed 타입 + 직접 하위 타입) — `typealias`·`fun main` 은 세지 않는다
- 관련 파일은 서브 패키지로 그룹화
- 검출 `OneClassPerFileConventionTest`(3레포) · 파일 이름 = 클래스 이름은 ktlint `standard:filename`

### 2. Model vs DTO vs Entity Usage
- **Model** (`model/`): Controller 레이어 request/response 전용
- **DTO** (`dto/`): Service 레이어 데이터 전송
- **Entity** (`persistence/`): DB 매핑 전용, 상위 레이어 노출 금지
- 변환 흐름: Entity ↔ DTO ↔ Model (MapStruct)
- 선언 형태(Model·DTO·JdbcEntity = data class + val, JPA Entity = class + var)·Model 전 프로퍼티 `@field:JsonProperty`·nullable 은 DB 와 일치·`!!` 금지·전부-기본값 금지·MapStruct `unmappedTargetPolicy = ERROR`(non-null 타깃 매핑 명시, `is`-prefix `@Mapping`) — [.claude/rules/conventions.md](.claude/rules/conventions.md) 「타입 선언」

### 3. Layered Service Pattern
- **Controller** → MappingService 주입 — 허용 목록은 MappingService 와 `config/`·`util/` 의 요청 경계 보조(Properties·Resolver)만, 그 외(BizService·EntityService·Repository·Client·임의 서비스)는 위반. 응답은 Model(요청 본문은 Dto 허용)
- **MappingService** → DTO↔Model 변환, BizService 호출 — 주입은 BizService·Mapper·Properties 만
- **BizService** → 비즈니스 로직, DTO 반환 — Repository 직접 주입 금지(EntityService·Client 위에 선다)
- **EntityService** → 영속성 작업, DTO 반환 — Entity 를 받지도 돌려주지도 않는다(의도 메서드)
- 접미사는 `~MappingService`·`~BizService`·`~EntityService`·`~Client`·`~Controller` 만 — 맨 `Service` 접미사는 계층을 숨긴다. 검출 `ServiceNamingConventionTest`·`LayeringConventionTest`

### 4. Transaction Management
- `@Transactional(readOnly = true)` - 조회 메서드 기본
- `@Transactional` - 변경 메서드만 적용
- 클래스 레벨에 기본 설정, 메서드 레벨에서 override
- **모든 `@Transactional` 에 `transactionManager` 명시(primary `mainTransactionManager` 포함)** — 경계는 BizService·EntityService(MappingService·HTTP 클라이언트 금지). 정본 `.claude/rules/conventions.md` 「데이터베이스」 3, 검출 `TransactionManagerConventionTest`
- `@Async` 는 같은 프록시 규칙 — 같은 클래스 안 호출은 동기. 비동기는 별도 빈(`ExampleAsyncProxyWorker`)으로. 검출 `AsyncSelfInvocationConventionTest`

### 5. Exception Handling Pattern
- 커스텀 예외는 `ExceptionCode` 인터페이스 구현
- `ErrorCode` enum으로 표준화된 에러 코드
- `GlobalExceptionHandler`에서 중앙 처리
- 주요 예외: `ItemNotFoundException`, `ClientBadRequestException`
- **오류 응답 계약은 세 가지다** — JSON 두 형식과 사람이 보는 HTML 화면 하나.

  | 경로 | 형식 |
  |---|---|
  | OAuth2 프로토콜 엔드포인트(`/oauth2/token`·`introspect`·`revoke`·`userinfo`, `/oauth2/authorize` 의 리다이렉트 전 오류) | `OAuth2ErrorResponseModel` — RFC 6749 §5.2 `{error, error_description, error_uri}` |
  | 인가 서버 브라우저 화면(`/oauth2/login`·`/oauth2/consent`)의 OAuth2 검증 실패 | HTML `oauth2/error`(`invalid_client` 401, 그 외 400, Location 없음) — 사람이 보는 화면이다 |
  | 그 밖의 모든 401·403·4xx (필터 체인 거부, `@PreAuthorize` 거부, CSRF 거부, 업무 예외) | `ErrorResponseModel` |

  필터 체인 단계는 `SecurityErrorResponseWriter`, 컨트롤러 이후는 `GlobalExceptionHandler` 가 같은 모델을 쓴다. 필터 체인 핸들러에 임시 Map 본문을 두지 않는다. 익명 요청의 `AccessDeniedException` 은 401 이다. 검출: `ErrorResponseFormatE2ETest`.

### 6. Caching Pattern
- `@CacheableNullSafe` 사용 (null 캐싱 방지)
- EntityService 레벨에서 캐싱 (영속성 계층과 가장 가까움)
- Redis 기반, TTL 설정 가능

### 7. String Enum Pattern
```kotlin
enum class UserStatus(override val value: String) : StringEnum {
    @JsonValue ACTIVE("active"),
    INACTIVE("inactive");
}
```
- `StringEnum` 인터페이스 구현
- JPA: `AbstractStringEnumConverter` 사용
- 변환: `StringEnumUtil.fromValue()` 사용

### 8. External API Client Pattern
```kotlin
@HttpExchange("/api")
interface ExternalApi {
    @GetExchange("/users/{id}")
    fun getUser(@PathVariable id: Long): UserResponse
}
```
- `ClientProperties`로 timeout, connection pool 설정
- `client/` 패키지의 `~Client`(`@Component`)로 래핑 — `CoreApiClient`·`ExampleWebClient`·`GoogleOAuth2Client`. BizService(`CoreApiBizService`)가 그것을 주입받고, MappingService 는 Client 를 직접 주입하지 않는다
- `WebClientUtil`로 재시도, 에러 처리

### 9. Async Processing Pattern
- `@Async` 는 별도 워커 빈에 둔다(`ExampleAsyncProxyWorker`) — 같은 클래스 안에서 부르면 프록시를 지나지 않아 동기로 돈다
- `MdcTaskDecorator`·`AuditorContextTaskDecorator` 로 traceId·감사 주체 전파
- requestId 로 상태 추적(`ExampleAsyncProxyStateStore`)
- 감사 기록(`AuditBizService`)은 비동기가 아니다 — 호출자 트랜잭션에 동기 참여

### 9-0. 로그인과 세션 발급
- 1st-party 세션(`user_sessions` 행·access/refresh 토큰·세션 캐시)은 **`SessionIssueBizService.issue` 한 곳에서만** 만들고, 그것을 부르는 곳은 인증 완료 지점 `LoginCompletionBizService.complete`(카운터 정리·발급·성공 감사) 하나다. 1단계 출구(`AuthBizService.login`·`SocialAuthBizService.socialLogin`)는 인증 판단(비밀번호 비교·소셜 검증·로그인 보호)을 끝낸 뒤 관문 `MfaChallengeBizService.afterFirstFactor` 만 부른다 — 새 로그인 경로도 이 관문을 지나야 2차 인증을 빠뜨리지 않는다
- 세션 일괄 폐기는 `UserSessionRevocationBizService.revokeAll(userId, reason, exceptSessionId)` 한 곳이다 — 호출자는 사유와 남길 세션만 정한다
- 잠긴 계정 거절은 `LoginAttemptBizService.accountLockedException` 으로 만든다(비밀번호·소셜·2단계가 같은 문구)
- `AuthBizService.verifyPassword` 는 로그인 보호(차단 IP·잠금·실패 기록)를 집행하고 확인된 사용자만 돌려준다. **성공해도 실패 카운터를 지우지 않는다** — 호출자가 인증이 최종적으로 끝난 지점에서 `clearAttempts` 를 부른다
- 소셜 로그인도 차단 IP(403)·잠긴 계정(423)을 거절한다(`SocialAuthBizService.socialLogin`) — 잠긴 계정이 소셜로 우회하지 못한다. 소셜 검증 실패는 비밀번호 추측이 아니라 실패 카운터를 올리지 않는다
- 비밀번호 로그인은 감사를 남긴다 — 실패는 `LOGIN_FAILURE`(사유 `BLOCKED_IP`·`ACCOUNT_LOCKED`·`INVALID_CREDENTIALS`, 사용자 ID 없음, `noRollbackFor` 로 커밋), 성공은 세션을 만든 뒤 `LOGIN_SUCCESS`(세션 ID 포함)
- 검출: `SessionIssueBizServiceTest` · `AuthBizServiceLoginProtectionTest` · `SocialAuthBizServiceLoginProtectionTest`(유닛) · `SocialBrowserLoginIntegrationTest`「로그인 보호」 · `LoginAuditE2ETest`

### 9-0a. 2차 인증 (TOTP)
- 사용자가 스스로 켠다(`/user/mfa` — setup → activate). 수단은 TOTP(RFC 6238, HMAC-SHA1·6자리·30초, ±1 구간)뿐이고 메일 코드·기기 기억은 없다. 시크릿은 `MfaSecretCipher`(AES-256-GCM, AAD `mfa:{userId}`, 키 `mfa-secret-encryption-key`)로 암호화해 `user_mfa_credentials` 에 둔다
- **켠 계정은 어느 로그인 경로로도 2단계 없이 세션을 받지 않는다** — `user_sessions` 행·토큰·브라우저 SecurityContext 는 2단계를 통과한 뒤에만 만든다. 1단계 출구 넷(`AuthBizService.login`·`loginForBrowser`, `SocialAuthBizService.socialLogin` 네이티브·콜백)은 `LoginOutcomeDto.MfaRequired` 로 challenge 만 만든다. 그래서 authorize·refresh·`/link`·`/user/**` 에는 별도 게이트가 없다
- 대기 상태는 Redis challenge(`MfaChallengeBizService`, `mfa:challenge:{sha256(token)}`, 5분, 저장·만료는 Lua 한 번) **하나뿐**이다 — 브라우저도 세션에는 토큰 문자열만 둔다(`MfaPendingTokenStore.begin` — 토큰을 두고 세션 ID 를 바꾼다). 완료(`complete`)는 존재 → 출처(`MfaOrigin`, API 경로는 `API_*`·브라우저는 `AUTHORIZE`·`SOCIAL_BROWSER` 만) → 차단 IP·잠금(코드 보기 전) → 코드 → `DEL` 반환 1 순서다. 출처가 틀리면 challenge 를 소비하지 않는다
- 같은 TOTP 구간은 한 번만 통과한다 — `last_used_step` 조건부 UPDATE 의 갱신 행 수(`UserMfaEntityService.markTotpStepUsed`)
- 2단계 실패는 비밀번호 실패와 **같은 계정 잠금 카운터**를 올리고(challenge 를 바꿔도 누적), 카운터는 **2단계 성공 때만** 지운다(1단계 성공은 지우지 않는다). challenge 하나에서 5번 틀리면 challenge 를 지운다(`FAIL_SCRIPT` — 키가 있을 때만 증가)
- 등록·관리 API 의 재인증은 비밀번호가 아니라 현재 코드다(소셜 전용 사용자는 비밀번호가 없다). 실패는 로그인 잠금과 별개인 `mfa:manage:attempt:{userId}`(5회/15분, 429)로 막는다. activate 는 현재 세션을 뺀 나머지 세션을 폐기한다
- 응답: API 1단계 `202 MfaChallengeModel{mfaToken, expiresIn, methods}` → `POST /auth/login/mfa`. 오류 `MfaException`(`MFA001`~`MFA006`, `docs/ERROR_CODES.md`)
- 검출: `TotpTest`·`Base32Test`·`MfaSecretCipherTest`(유닛) · `MfaChallengeIntegrationTest`(재사용·동시 8건 중 1건·출처·한도·잠금 우선·1단계가 카운터를 지우지 않음) · `SocialBrowserLoginIntegrationTest`「2차 인증」 · `MfaE2ETest`

### 9-1. Session Ownership (세션 표면의 인가)
- 세션 폐기 표면 둘(`DELETE /users/me/sessions/{sessionId}` — JWT 의 `jti`, `DELETE /user/sessions/{id}` — 행 id)은 **요청자(principal) 소유 세션만** 폐기한다 — 타인 세션은 403(`ForbiddenException`)/400, 없는 세션은 204(멱등). 경로는 `Controller → UserSelfMappingService → UserSelfBizService.revokeSessionBySessionId → UserEntityService`
- `GET /user/sessions` 의 `sessionId` 는 마스킹(8자 + `****`)이다 — 클라이언트에 원본 세션 식별자를 주지 않는다
- 검출: `UserSelfBizServiceSessionTest`(유닛) · `UserControllerTest`(슬라이스 403/204) · `SessionOwnershipE2ETest`(e2e)

### 9-2. Notification (메일 발송 포트)
- `service/notification/MailSender` 포트 하나 — 기본 구현 `LoggingMailSender` 는 **수신자(마스킹 `ab***@…`)·제목만** INFO 로그, 본문은 어떤 프로파일에서도 로그에 남기지 않는다(토큰이 들어 있다)
- 로컬 데모·e2e 는 `DemoInboxMailSender`(`@Profile("local", "e2e")`, `@Primary`)로 토큰을 받는다 — e2e 는 빈을 주입받아 `latest(to)`, local 은 `app.mail.demo-mailbox-dir`(`~/.skeleton/demo-mailbox`, 디렉터리 0700·파일 0600)의 수신자별 파일. **수신함을 HTTP 로 열지 않는다** — 수신자 외 누구나 읽는 표면은 프로파일이 무엇이든 계정 탈취 오라클이다
- 비밀번호 재설정·이메일 인증 토큰은 `UserSelfBizService` 가 저장 뒤 **메일로만** 보낸다(서비스가 원본 토큰을 반환하지 않는다). `send` 가 던지면 같은 auth 트랜잭션이 롤백된다 — DB 에 남는 토큰은 전부 발송된 것(`MailSendFailureIntegrationTest`); 발송 뒤 커밋 실패는 메일의 토큰이 무효가 될 뿐(재요청)
- 실제 발송(SMTP·SES)은 파생 프로젝트가 `MailSender` 구현 빈을 하나 더 두고 `@Primary` 로 올린다 — 계정·발신 도메인·정확히-한-번 배달(outbox)은 스켈레톤이 정하지 않는다

### 9-2a. 인가 서버 클라이언트 인증
- `/oauth2/token`·`introspect`·`revoke` 의 클라이언트 인증은 **시크릿 필수**다(`OAuth2AuthorizationBizService.authenticateClient`). 모든 클라이언트가 시크릿을 갖고 public 클라이언트 유형이 없으므로, 시크릿 없이 `client_id` 만 통과시키면 클라이언트 인증 우회다
- 방식은 `client_secret_basic`(헤더)·`client_secret_post`(폼) 둘이고 한 요청에 하나만 — `ClientCredentialsResolver`(`config/security`)가 꺼낸다. `invalid_client` 는 401 + `WWW-Authenticate: Basic realm="oauth2"`
- 검출: `ClientCredentialsResolverTest`(유닛) · `OAuth2AuthorizationControllerTest`(Basic·헤더) · `OAuth2AuthorizationE2ETest`「클라이언트 인증 방식」

### 9-2b. OIDC userinfo · email_verified
- `/oauth2/userinfo` 는 **이 인가 서버가 발급한 access 토큰**만 받는다(`OAuth2AuthorizationBizService.resolveUserInfoGrant` — 서명·만료, `oauth2_access_tokens` 활성 기록, 사용자 주체, `openid` scope). 1st-party 로그인 토큰은 받지 않는다. API 체인에서는 permitAll 이고 컨트롤러가 Bearer 를 직접 꺼낸다. 오류는 RFC 6750(401 `invalid_token`·403 `insufficient_scope` + `WWW-Authenticate`)
- 클레임은 토큰 scope 만큼만(`profile`·`email`), 나머지는 응답에서 빠진다(`UserInfoModel` `NON_NULL`)
- `email_verified` 의 정본은 `TokenEntityService.existsVerifiedEmail` — 현재 이메일의 인증 토큰에 `verified_at` 이 있는가. `is_verified` 로 보지 않는다(재요청 무효화도 `is_verified=true` 를 쓴다). userinfo · id_token · `GET /user/profile` 이 같은 규칙을 쓴다
- 검출: `OAuth2AuthorizationBizServiceUserInfoTest`(유닛) · `EmailVerifiedQueryIntegrationTest`(실제 MySQL) · `OidcE2ETest` · `EmailVerifiedE2ETest`
- id_token `auth_time` 은 **사용자가 인증한 시각**이다(발급 시각이 아니다). 원천은 브라우저 principal 의 사용자 세션 `user_sessions.created_at`(`SessionInfo.authenticatedAt`) — authorize·consent 가 세션 ID 로 읽어 인가 코드 `auth_time` 에 저장하고, 토큰 교환이 그 값을 쓴다. 세션이 사라졌으면 authorize 는 로그인 화면, consent 는 `login_required`. 배포 전 코드(`auth_time` NULL)는 발급 시각으로 대체한다. refresh grant 는 id_token 을 내지 않는다
- 세션 캐시를 읽지 못하면(배포 전 형식 등) 그 키를 지우고 DB 에서 다시 읽는다 — 인증 실패로 끝내지 않는다(`SessionCacheRevocationTest` SR-L)
- 검출: `AuthTimeE2ETest`(직접 발급·동의 후 발급)
- **discovery 는 실제 지원값에서 만든다.** 지원값 상수(`OAuth2AuthorizationBizService.SUPPORTED_RESPONSE_TYPES`·`SUPPORTED_GRANT_TYPES`·`SUPPORTED_CODE_CHALLENGE_METHODS`·`SUPPORTED_TOKEN_ENDPOINT_AUTH_METHODS`·`SUPPORTED_SCOPES`, `IdTokenBizService.SUPPORTED_SIGNING_ALGORITHMS`·`SUPPORTED_CLAIMS`)가 광고와 검사 로직의 유일한 정본이다. `OpenIdConfigurationModel` 에 기본값을 두지 않는다. 지원을 늘리거나 줄이면 상수와 처리 로직을 같이 바꾼다 — `DiscoveryConformanceE2ETest` 가 광고값마다 실제 동작과 클레임 출력을 대조한다(광고했는데 안 되는 것·되는데 광고하지 않은 것 모두 실패)

### 9-3. 브라우저 세션 체인 (소셜 로그인·브라우저 흐름)
- 필터 체인은 둘이다. **브라우저 체인**(`@Order(1)`, 경로 정본 `config/security/browser/BrowserSecurityPaths`)은 세션(`SKELETON_SESSION`, Spring Session Redis)·CSRF 를 쓰고 JWT 를 읽지 않는다. **API 체인**(`@Order(2)`)은 STATELESS·Bearer 그대로다. 브라우저 체인은 CORS 를 끈다(쿠키를 싣는 경로를 교차 출처에 열지 않는다)
- `JwtAuthenticationFilter` 는 서블릿 자동 등록을 끈다(`jwtAuthenticationFilterRegistration`) — 켜 두면 브라우저 경로에서도 Bearer 가 SecurityContext 를 채운다
- 세션에는 SecurityContext·CSRF 토큰·문자열만 둔다(JDK 직렬화 — `AuthenticatedUser` 는 `Serializable`). 요청 캐시는 끈다
- **1회 소비 값은 세션 속성이 아니라 Redis `GETDEL`** 로 다룬다 — 세션은 요청마다 읽은 사본이라 동시 요청이 같은 값을 둘 다 소비할 수 있다
- 소셜 state: `GET /auth/oauth2/{p}/authorize` 가 발급해 `browser:social-state:{state}`(TTL 5분)에 `{browserSessionId, provider}` 로 묶고 provider 로 302 한다. 콜백은 **무엇보다 먼저** 소비하고, 세션·provider 가 다르면 코드 교환 없이 `error=invalid_state` 다. `POST /auth/oauth2/{p}/login`·`/link` 는 네이티브 코드 교환이라 state 검증은 클라이언트 책임이다
- **소셜 콜백은 토큰을 URL 에 싣지 않는다.** 성공하면 토큰을 `browser:token-handoff:{userSessionId}`(TTL 60초)에 맡기고, `changeSessionId` 뒤 SecurityContext 를 세션에 저장(`BrowserSessionAuthenticator`)하고, 쿼리 없이 `/demo/auth/callback` 으로 보낸다. 페이지는 세션 쿠키 + CSRF 헤더로 `POST /auth/session/token` 을 한 번 부른다(두 번째·미로그인 401, CSRF 없음 403)
- **인가 서버 브라우저 흐름**: `/oauth2/authorize`·`/oauth2/login`·`/oauth2/consent`·`/auth/mfa`(2단계 화면, 「9-0a」) 는 브라우저 체인이다 — authorize 는 브라우저 세션으로만 로그인을 판정한다. `LOGIN_REQUIRED` 면 원래 authorize URL 을 서버가 다시 조립해 세션(`AuthorizationRequestStore`, `/oauth2/authorize?` 접두사 강제)에 두고 `/oauth2/login` 으로 보낸다 — 복귀 주소를 쿼리로 받지 않는다(오픈 리다이렉트). 로그인(`OAuth2BrowserController`)은 `AuthBizService.loginForBrowser` → `login` 을 그대로 지나 로그인 보호를 재사용하고 토큰을 내주지 않는다. 동의는 HTML 폼(`templates/oauth2/`, 데모 fragments 에 기대지 않는다)이고 결과를 저장하지 않는다. 화면의 OAuth2 검증 실패는 `oauth2/error` HTML(401/400), Location 없음
- 브라우저 세션 principal 은 사용자 세션 ID 를 든다. `BrowserSessionValidationFilter` 가 요청마다 `validateSession` 으로 폐기를 보고 세션을 버린다 — 로그아웃·전체 로그아웃·비밀번호 변경이 브라우저 세션에도 걸린다. 일괄 폐기는 **폐기 전에** 대상 세션을 모으고 **커밋 뒤에** 그 캐시를 지운다(`UserSessionRevocationBizService.revokeAll` → `SessionCacheBizService.evictRevokedSessions`) — 폐기 뒤 "살아 있는 세션" 으로 찾으면 방금 폐기한 세션의 캐시가 남고, 커밋 전에 지우면 다른 요청이 폐기 전 행을 다시 캐시에 올린다. `deleteAllUserSessions` 는 권한 갱신 전용이다. 조회 쪽 캐시 miss 는 `cacheSessionUnlessRevoked` — 폐기 표식(`session:revoked:{id}`)이 없을 때만 쓰는 Lua 스크립트(해시 태그로 같은 슬롯)라, 폐기가 끝난 뒤 폐기 전 값이 다시 캐시에 나타나지 않는다
- 쿠키: HttpOnly · `SameSite=Lax`(Strict 면 provider 콜백에 쿠키가 실리지 않는다) · Secure 는 논리 키 `browser-session-cookie-secure`(기본 `true`, `local`·통합 테스트 `false`)
- 검출: `SocialAuthorizationStateBizServiceTest`·`TokenHandoffBizServiceTest`(유닛) · `SocialBrowserLoginIntegrationTest`(실제 체인·Redis 클러스터, 세션 복원, 동시성) · `SocialAuthE2ETest` · `AuthorizationRequestStoreTest` · `OAuth2BrowserControllerTest` · `OAuth2AuthorizationE2ETest`(로그인·동의 왕복) · `SessionOwnershipE2ETest` O-4·O-5(일괄 폐기)

### 10. Performance Monitoring
- `@SlowMethod` 어노테이션으로 느린 메서드 감지
- 설정 가능한 임계값, 인자 로깅
- MDC traceId 연동

### 11. Distributed Tracing
- `TraceIdFilter`가 `X-Trace-Id` 헤더 추출/생성
- MDC에 저장하여 모든 로그에 포함
- `ClientConfiguration` 의 `ExchangeFilterFunction` 이 `@HttpExchange`·`WebClient` 아웃바운드 호출에 `X-Trace-Id` 로 전파

### 12. JPA Auditing
- `BaseAuditedEntity` 상속으로 자동 감사 필드
- 감사 주체는 `SimpleAuditorAware` 가 **`AuditorContext`** 에서 읽는다. **MDC 는 로깅 전용이고 감사에 관여하지 않는다** — 감사 주체가 비면 틀린 값이 DB 에 남으므로 채널을 나눴다
- 이 레포는 **인증 주체에서 도출한다** — `SecurityAuditorContextFilter` 가 `JwtAuthenticationFilter` 직후에 돌며 `AuthenticatedUser.userId` 를 넣는다. **인증이 헤더를 이긴다**
- 인증이 없을 때만 `X-User-Id` 헤더로 폴백하며, **`app.audit.trust-user-header` 가 켜져 있을 때뿐이다(기본 `false`)**
- **플래그를 켜는 것은 이 코드가 강제하지 않는 배포 계약을 단언하는 행위다** — 게이트웨이를 통해서만 도달 가능하고, 게이트웨이가 자기 인증 결과로 `X-User-Id` 를 덮어써 호출자가 위조할 수 없어야 한다. **그 경계 없이 켜지 않는다.** 게이트웨이가 헤더에 서명하면 서비스가 검증할 수 있는데, 그것은 인프라 변경이지 인증 프레임워크 도입이 아니다
- 둘 다 없으면 `SYSTEM_USER_ID`. `Long` → `Int` 는 범위를 벗어나면 폴백한다 — **잘라서 다른 사용자 ID 로 기록하지 않는다**
- 비동기 전파는 `AsyncConfiguration` 이 `MdcTaskDecorator` 와 `AuditorContextTaskDecorator` 를 합성해 배선한다
- `AuditConfiguration` 은 `@EnableJpaAuditing(auditorAwareRef = "auditorProvider")` 로 참조를 명시한다

## Environment Configuration

- **Profiles**: 비밀값 출처만 고른다 — `local`(docker compose 값) · `aws` · `azure` · `gcp`, 조합용 `loki`. 환경(dev·prod)별 프로필은 없다 — 환경 차이는 환경변수로 준다. 테스트 전용: `testcontainers`(통합), `test`(`TestSecurityConfiguration`), `e2e`(local 위에 얹음)
- **Configuration Files**: `application.yml`(논리 키 참조) + `application-{local,aws,azure,gcp}.yml`
- **설정값·비밀값**: yml 은 kebab-case 논리 키만 참조, 평문 금지. 정본 [.claude/rules/conventions.md](.claude/rules/conventions.md) 「설정값·비밀값 규약」. 논리 키 목록은 README 「설정과 비밀값」·`.env.example`
- **로컬 개발 환경**: `docker compose up -d` 로 MySQL·Redis 를 띄우고 `local` 프로필로 붙는다. `skeleton_main` DDL 은 `db/manual/create-main.sql`(compose·Testcontainers 공용), `skeleton_auth` 는 Flyway
- **Logging**: `logback-spring.xml` 하나 — `<springProfile>` 분기. 콘솔(+파일), `loki` 프로필을 조합하면 Loki
- **Dependencies**: `build.gradle.kts`에 직접 정의 (version catalog 미사용)

## Important Notes

1. **한국어 명명 및 문서화** 규칙 사용
2. **ktlint 엄격 적용** - 커밋 전 `./gradlew ktlintFormat` 실행
3. **MDC 컨텍스트 유지** - 모든 비동기 작업에서 traceId 전파
4. **Swagger 문서** - `/swagger-ui.html`에서 확인
5. **계층 우회 금지** - Controller → EntityService 직접 호출 금지
6. **순환 참조 주의** - Service 간 순환 의존성 발생 시 별도 Config로 분리
7. **데모 페이지는 실제 API 계약을 따른다**(`templates/demo/admin`) — 응답 필드는 Model 의 `@JsonProperty` 이름, 경로는 `controller/admin` 매핑, 요청 본문은 핸들러 `@RequestBody` DTO 의 파라미터. 검출 `DemoTemplateContractTest`·`DemoAdminPagesBrowserE2ETest`. 사용자 목록 API 는 없다(대시보드 사용자 수는 `—`). 서버 값은 `innerHTML` 에 넣기 전 `escapeHtml()` 을 지나고, **인라인 이벤트 핸들러(`onclick="…"`)에는 보간을 넣지 않는다** — HTML 이스케이프는 JS 문자열 문맥을 지켜 주지 못하므로 값은 `data-*` 로 넘기고 클릭은 위임 `addEventListener` 가 받는다

---

## 미결 항목 처리

- 지적을 "별도 티켓" 으로 미루려면 **티켓 식별자·담당·시점**을 함께 적는다. 셋 중 하나라도 없으면 그것은 티켓이 아니라 **미해결로 방치한 것**이므로, 지금 처리하거나 사용자에게 판단을 요청한다.
- "나중에 한다" 를 선택할 때는 **미루는 이득**을 한 줄로 적는다. 적을 수 없으면 미루지 않는다.
- 문서에 남긴 미결 항목은 다음 게이트에서 다시 확인한다.

### 테스트 소스셋에서 MapStruct 를 쓰려면

테스트 소스셋(`src/test/{unit,integration,e2e}`)에서 `@Mapper` 를 **직접 선언**하려면 그 소스셋에 kapt 설정을 추가한다.

```kotlin
val kaptIntegrationTest: Configuration by configurations.getting {
    extendsFrom(configurations.getByName("kapt"))
}
```

현재는 세 스켈레톤 어디에도 테스트 소스셋 `@Mapper` 가 없어 설정을 두지 않는다. 테스트가 쓰는 `*MapperImpl` 은 `src/main` 에서 생성돼 test 컴파일 클래스패스에 이미 올라온다.


## MySQL 스키마 및 계정

- 스키마는 `skeleton_main`(@Primary — 업무 예제, 수동 DDL) · `skeleton_auth`(인증·인가, Flyway) 다. 업무 테이블은 기동 시 자동 생성/변경하지 않는다 — 각 EMF 가 `hibernate.hbm2ddl.auto=none` 이다.
- `skeleton_main` DDL 정본은 `src/main/resources/db/manual/create-main.sql` 이다. docker compose 초기화(`docker/mysql/init`)와 Testcontainers(`withInitScript`)가 같은 파일을 쓴다.
- 런타임 read/write 는 `skeleton_reader`/`skeleton_writer` 를 쓴다. 논리 키는 `db-{reader,writer}-{username,password}` 다.
- 스키마·테이블의 문자셋·콜레이션은 `utf8mb4` / `utf8mb4_0900_ai_ci` 다 — DDL(Flyway V1·수동 SQL)의 `COLLATE` 명시값과 스키마 기본이 같다. 이미 적용된 Flyway 마이그레이션 파일은 고치지 않는다 — 체크섬이 어긋난다(앱은 repair 를 쓰지 않는다). 새 버전을 추가한다.
- Flyway 는 `skeleton_auth` 만 관리하고 이력은 `flyway_schema_history_public_api` 에 기록한다. `spring.flyway.enabled` 는 기본 `false`, `local` 은 `true` 다.
- Flyway 는 `skeleton_admin` 을 쓴다. `app.flyway.mysql.username/password` 는 논리 키 `db-admin-username`/`db-admin-password` 를 참조한다.
- 지정한 catalog 이외 URL 은 연결 전에 거부한다. 자동 스키마 생성, baseline, repair 는 사용하지 않는다.

## 코드 정리·재검증 범위

- `/simplify` 는 구현자가 테스트 통과 후, 코드 리뷰(PR) 전에 1회 실행한다. 단일 파일 수준의 소규모 변경은 생략 가능. 실행 후 테스트를 재확인한다. 리뷰어는 읽기·판정 전용이므로 실행하지 않는다 — 가독성 문제는 Request Changes 로 돌려보낸다.
- 리뷰·검증에서 발견한 문제를 수정한 뒤의 재검증은 변경 범위에 맞춘다. 검증 리포트 또는 PR 본문에 재검증 범위와 근거(변경 파일 → 영향 경로)를 적는다. "처음부터 전체" 는 횡단 변경일 때만 한다.

| 수정의 변경 범위 | 재검증 범위 |
|---|---|
| 국소 — 단일 모듈 내부, 공용 코드 미변경 | 해당 모듈 테스트 재실행 + 변경 지점과 그것을 쓰는 경로 |
| 공통 — 공용 설정·베이스 클래스·공용 라이브러리 | 사용처를 Grep 으로 열거해 적고, 그 경로들을 재검증 |
| 횡단 — 규약 변경·인증/보안 계층 | `./gradlew check` 전체 재실행 |

## 에이전트 지침

에이전트 작업의 진입점은 [AGENTS.md](AGENTS.md)다. 프로젝트 규칙은 `CLAUDE.md`와 README 「코딩 규약」 원본을 공유한다. `.claude/rules/conventions.md`(front matter `paths`: `**/*.kt` · `**/build.gradle*` · `**/*.sql` · `**/*.yml`)가 있으며, `AGENTS.md`의 탐색 절차에 따라 그 경로를 만지는 작업이면 본문을 읽고 적용한다. 규칙 본문을 별도 사본으로 복제하지 않는다.
