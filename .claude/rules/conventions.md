---
description: public-api 스켈레톤 코드 컨벤션 핵심 체크포인트 — Kotlin·Gradle·SQL·설정 파일 작업 시 자동 로드. 명명 표·접미사·1클래스1파일·String Enum 의 정본은 README 「코딩 규약」.
paths:
  - "**/*.kt"
  - "**/build.gradle*"
  - "**/*.sql"
  - "**/*.yml"
---

# 코드 컨벤션 (핵심 체크포인트) — public-api 스켈레톤

본 룰은 코드·설정 작업에서 자주 참조되는 규칙만 담는다. 명명 표·클래스 접미사·1 Class per 1 File·String Enum 의 정본은 [README 「코딩 규약」](../../README.md#코딩-규약), 레이어드 상세는 [CLAUDE.md](../../CLAUDE.md) 다. 세 스켈레톤(public-api·internal-api·batch)의 「데이터베이스」「타입 선언」「설정값·비밀값 규약」 절은 **글자 단위로 동일**하다 — 고치려면 셋을 함께 고친다.

## 데이터베이스

1. **식별자는 `Long` / `BIGINT`.** 이 프로젝트가 소유하는 테이블의 PK·FK 컬럼은 `BIGINT`. Kotlin 의 식별자(PK·FK)와 감사 주체(`createdBy`·`updatedBy`·`deletedBy`) 프로퍼티는 **출처 DB 의 컬럼 폭과 무관하게 항상 `Long`** 이다. 다른 시스템이 소유한 외부 스키마의 `INT` 컬럼은 그쪽 정본을 두고 Kotlin 에서 `Long` 으로 넓혀 받는다(JDBC 는 `getLong`). `Int` 식별자를 새로 만들지 않는다.
2. **업무 DDL 에 `FOREIGN KEY` 제약을 쓰지 않는다.** 참조는 논리 FK 로 유지하고 참조 컬럼에 `KEY` 인덱스를 둔다. 무결성은 애플리케이션이 관리한다. 예외는 Spring Batch·Quartz 메타 스키마(프레임워크 정본 DDL)뿐이다.
3. **트랜잭션.** 클래스 레벨 `@Transactional(readOnly = true)` 를 기본으로 두고 변경 메서드에만 `@Transactional`. **모든 `@Transactional` 에 `transactionManager = "<데이터소스>TransactionManager"` 를 명시한다 — primary(API `mainTransactionManager` · batch `batchTransactionManager`)를 쓰는 경우도 적는다.** 데이터소스가 여럿이라 묵시적 바인딩은 primary 로 가고, 다른 데이터소스의 리포지토리는 그 트랜잭션 밖에서 실행된다 — `@Modifying` 은 `TransactionRequiredException`, 인터페이스에 선언한 파생 `deleteBy…` 는 예외 없이 **조용히 버려지며**(다른 트랜잭션 매니저 의 트랜잭션이 살아 있어 `remove` 는 받아들여지지만 flush 되지 않는다), 읽기 뒤 쓰기는 커넥션을 둘 잡는다(local·테스트 풀 1 에서 타임아웃). `REQUIRES_NEW` 는 같은 풀에 커넥션이 하나 더 필요하다 — 풀 크기와 함께 결정하고 기본은 참여(`REQUIRED`)다. `@Transactional` 은 프록시가 가로채는 public·open 메서드에만 둔다 — `private`·`final`·자기 호출(`this.method()`) 경로는 무효다. `@Async` 도 같은 프록시 규칙이다 — 같은 클래스 안에서 부르면 프록시를 지나지 않아 **동기로 돈다**(감사 기록·백그라운드 처리가 비동기라고 믿고 짠 코드가 요청 스레드에서 실행된다). 비동기가 필요하면 `@Async` 메서드를 **별도 빈**으로 나누고 호출자는 그 빈을 주입받는다(`ExampleAsyncProxyWorker`). 검출: `src/test/unit/…/convention/AsyncSelfInvocationConventionTest`(소스 스캔 — `@Async fun X` 와 같은 파일 안의 무한정 `X(` 호출). 트랜잭션 경계는 BizService·EntityService 다 — MappingService·HTTP 클라이언트 서비스에는 붙이지 않는다. `readOnly` 는 데이터소스를 바꾸지 않는다 — read/write 분리는 명시적 DataSource 이름으로 한다. `@EnableJpaRepositories`·`@EnableJdbcRepositories` 의 `transactionManagerRef` 는 같은 이름의 `PlatformTransactionManager` `@Bean` 이 있어야 한다(없으면 기동이 아니라 그 패키지의 인터페이스 리포지토리 첫 호출에서 `NoSuchBeanDefinitionException` — 인터페이스 리포지토리가 없는 동안은 잠재한다). 검출: `src/test/unit/…/convention/TransactionManagerConventionTest`(리플렉션 — 트랜잭션 매니저 명시 · private/final 금지 · MappingService 금지 · `transactionManagerRef` 정합).

## 명명

- 약어 금지 — 역할 코드·Enum 값·상수는 풀네임(`ADMINISTRATOR`, `TEAM_LEADER`). 정본은 README 「코딩 규약」.
- 메서드명은 단수·복수를 엄격히 구분한다 — 단수 반환 `find` · `get` · `getBy…` · `findBy…`, 복수 반환 `findAll` · `findAllBy…` · `getAll` · `getAllBy…` · `list`. 생성 `add`/`create`, 수정 `modify`/`update`, 삭제 `remove`/`delete`.
- 패키지 `enums/`(`enum/` 금지 — Java 예약어라 MapStruct 빌드가 깨진다).

## 레이어드 아키텍처

- `Controller → MappingService → BizService → EntityService → Repository`. 계층 우회 금지 — Controller 는 MappingService 만 주입받고 Model 을 응답한다(요청 본문은 DTO 허용; 주입 허용 목록은 MappingService 와 `util/`·`config/` 의 요청 경계 보조 컴포넌트만 — 그 외는 전부 위반). MappingService 는 BizService·Mapper·Properties 만 주입받는다. BizService 는 Repository 를 직접 주입하지 않는다. 외부 API 어댑터는 `client/` 의 `~Client` 이고 BizService 아래에 둔다. Entity 는 EntityService 위로 올라가지 않는다. 검출 `ServiceNamingConventionTest`·`LayeringConventionTest`.
- **API 스켈레톤에 `@Scheduled` 를 쓰지 않는다.** 스케줄·배치 작업은 `spring-batch-skeleton` 에서 파생한 별도 배치 프로젝트로 분리한다.

## 타입 선언 (Model·DTO·JdbcEntity·Entity)

- **선언 형태는 계층이 정한다.** Model(API 입출력) · DTO(계층 간 전송) · Spring Data JDBC `*JdbcEntity` 는 **`data class` + `val` + 생성자 매핑** — MapStruct 는 주 생성자로 채운다. JPA `*JpaEntity`/`*Entity`(`@Entity`·`@MappedSuperclass`)는 **`class` + `var`** — `data class` 금지(가변 필드 equals/hashCode·lazy 연관 toString·프록시), `plugin.jpa` 의 no-arg 생성자 + setter 로 MapStruct 가 채운다. 예외 없음 — 조회 전용 엔티티도 `var`.
- Model 의 **모든 프로퍼티에 `@field:JsonProperty("이름")`** 을 명시한다. 이름은 camelCase 그대로, 외부 계약이 snake_case 를 요구하면 그 이름. 클래스 본문 프로퍼티(`val status: Int = code.status` 류)도 대상이다.
- **nullable 을 지양한다 — non-null 이 기본.** Entity·JdbcEntity 와 그것을 옮기는 DTO/Model 의 `?` 는 **DB 컬럼의 NULL 허용과 일치**시킨다(`@Column(nullable = false)` 인데 `T?` 인 조합, 그 역 — 둘 다 금지). `= null` 을 습관적으로 붙이지 않는다. **`!!` 를 쓰지 않는다** — `?:` 로 예외(`NotFoundException` 류)를 던지거나 기본값을 준다. 요청 Model 의 필수 필드는 non-null 타입 **과 함께** `@field:NotNull`/`@NotBlank`/`@NotEmpty` 를 붙이고 컨트롤러에서 `@Valid` 로 검증한다 — Jackson 역직렬화 실패의 400 이 아니라 필드 단위 400 메시지를 준다.
- **모든 생성자 파라미터에 기본값을 주지 않는다.** 전부 기본값이면 Kotlin 이 파라미터 없는 생성자를 만들고, MapStruct 는 그것을 골라 setter 로 채우려 한다 — `val` 은 setter 가 없어 **빈 객체가 반환**되며 컴파일 에러도 경고도 없다. 정말 전부 optional 이어야 하면 생성자에 MapStruct 의 `@Default` 를 붙여 생성자 매핑을 강제한다(`annotation class Default` 하나를 두고 `data class X @Default constructor(…)`). Entity 는 no-arg 생성자 요건상 body `var x: T = 초기값` 이 정상이다.
- `@MappingTarget` 갱신 매핑을 쓸 때만 그 대상 프로퍼티를 `var` 로 선언한다.
- **MapStruct 는 `MapstructConfiguration` 의 `unmappedTargetPolicy = ERROR` 아래에서 돈다**(3레포 동일). 소스에 없는 타깃은 `@Mapping(target = "x", ignore = true)` 를 명시해야 컴파일된다. **non-null 타깃 프로퍼티는 `ignore` 금지** — `source`·`expression`·`constant` 로 매핑을 명시한다. 생성자 매핑이면 `null` 이 생성자에 들어가 런타임 NPE, JPA Entity 도 예외가 아니다 — `plugin.jpa` 가 만드는 no-arg 생성자는 **초기화식을 실행하지 않아**(바이트코드 `super(); return`) 미매핑 프로퍼티가 `= 초기값` 과 무관하게 `null`/`0` 이 된다. `ignore = true` 는 nullable 타깃과 `@Id`(DB 생성 키) 에만 허용한다. nullable 소스를 non-null 타깃에 매핑할 때는 `expression`(null-safe) 또는 `defaultValue`/`defaultExpression` 으로 null 경로를 닫는다(`qualifiedByName` 은 그 `@Named` 함수가 non-null 을 반환할 때만). `source` 만 명시하는 것은 매핑을 「명시」했을 뿐 함정을 없애지 않는다. Entity 타깃(setter 매핑)도 같은 규칙이다. **`is`-prefix Boolean 은 `@Mapping(source = "x", target = "isX")` 를 명시한다** — getter 가 `isX()` 라 소스 프로퍼티명은 `x` 인데 생성자 파라미터는 `isX` 여서, 명시하지 않으면 미매핑으로 **항상 `false`** 가 된다(IGNORE 정책이 덮고 있던 결함). `unmappedSourcePolicy` 는 IGNORE — 소스가 타깃보다 넓은 것은 정상이다.
- **JPA 연관 관계·Join 을 쓰지 않는다.** `@OneToMany`·`@ManyToOne`·`@OneToOne`·`@ManyToMany`·`JOIN FETCH` 금지. 복잡 조회는 JDBC(`NamedParameterJdbcOperations`, `*JdbcEntityService`)로 한다.
- 검출: `src/test/unit/…/convention/DeclarationStyleConventionTest`(선언 형태·`var`·전부-기본값·`nullable = false` 정합·Model `@JsonProperty` — 리플렉션)와 `src/test/unit/…/mapper/MapStructConstructorMappingTest`(생성물에 setter 경로 없음 · `ERROR` 정책 · non-null 타깃 `ignore` 없음 · nullable 소스→non-null 타깃 null 경로 닫힘).

## 환경 설정

- 프로필은 **비밀값 출처**만 고른다 — `local`(docker compose 값) · `aws` · `azure` · `gcp`, 조합용 `loki`. 환경(dev·staging·prod)별 프로필을 만들지 않는다 — 환경 차이(호스트·풀 크기·로그 레벨)는 배포 시 환경변수로 준다. 기본 활성 프로필은 없다(`bootRun` 만 `SPRING_PROFILES_ACTIVE` 가 없으면 `local`).
- **로컬 개발 환경**: `docker compose up -d` 로 MySQL·Redis 를 띄우고 `local` 프로필로 붙는다. 스키마·계정은 `docker/mysql/init`, `skeleton_main` 업무 테이블은 `db/manual/create-main.sql`(Testcontainers 와 공용), `skeleton_auth` 는 `local` 이 켠 Flyway(`skeleton_admin`, e2e 시드 `dml/test` 포함)가 만든다(`CLAUDE.md` 「MySQL 스키마 및 계정」). 통합 테스트는 Testcontainers 로 격리한다.
- 논리 키가 해석되지 않으면 **`UnresolvedPlaceholderGuard`** 가 환경 준비 단계에서 기동을 막는다 — `IllegalStateException: 해석되지 않은 설정 플레이스홀더 …` 뒤에 `프로퍼티 -> ${이름}` 목록이 붙는다. Spring Boot 의 `@ConfigurationProperties` 바인더는 해석 불가 `${…}` 를 리터럴로 남겨 조용히 뜨게 하므로(`ignoreUnresolvablePlaceholders`) 이 가드가 규약의 「폴백 없이 엄격」을 보증한다(`config/UnresolvedPlaceholderGuard.kt`, `META-INF/spring.factories`, 3레포 동일 — 등록 경로는 `UnresolvedPlaceholderGuardBootIntegrationTest` 가 `guardprobe` 프로필로 실제 기동해 확인한다). 키 이름 오타 → 비밀 저장소 미등록 → 프로필·자격증명 순으로 본다. 테스트 프로필 yml(`application-testcontainers.yml`)은 논리 키에 고정 테스트 값을 둔다(예외 ②).

### 설정값·비밀값 규약

- 애플리케이션 yml 은 비밀값과 환경마다 달라지는 값을 **kebab-case 논리 키**(`${db-writer-password}`, `${db-host}`)로만 참조한다. 값은 ① 환경변수(Spring 이 `DB_WRITER_PASSWORD` 를 `db-writer-password` 로 대응시킨다) 또는 ② 비밀값 출처 프로필 — `aws`(Secrets Manager JSON 비밀의 키) · `azure`(같은 이름의 Key Vault 비밀) · `gcp`(같은 이름의 Secret Manager 비밀, `application-gcp.yml` 에 키마다 `${sm@…}` 대응) — 이 채운다. 논리 키는 영숫자와 하이픈만 쓴다(Key Vault 이름 제약).
- 비밀(계정·비밀번호·API 키·토큰·서명키)은 논리 키 참조에 **기본값을 두지 않는다** — 참조는 폴백 없이 엄격해서 키 오타·미등록은 기동 실패로 드러난다(조용한 미설정을 두지 않는다). 비밀이 아닌 값(포트·타임아웃·cron 등)은 yml 에 값으로 적거나 논리 키에 기본값(`${db-port:3306}`)을 둔다.
- 자격증명 평문은 어느 yml 에도 적지 않는다. `local` 프로필의 docker compose 계정만 예외다(로컬 컨테이너 전용 값).
- 논리 키를 추가하면 README 「설정과 비밀값」 표, `.env.example`, `application-gcp.yml`(비밀이면)을 함께 고친다 — 검출: `SecretKeyCoverageTest`.
- 예외 둘: ① 프로필 활성화(`SPRING_PROFILES_ACTIVE` / `--spring.profiles.active`)와 비밀 저장소 위치(`AWS_SECRET_ID` · `AZURE_KEYVAULT_ENDPOINT` · `GCP_PROJECT_ID`)는 설정값이 아니라 실행 선택이므로 환경변수 이름 그대로 쓴다. ② 테스트 소스셋(`src/test/**`)의 고정 테스트 값과, 미설정이면 해당 테스트만 skip 되는 선택적 e2e 계정 환경변수(`E2E_*`·소셜 `*_CLIENT_ID/SECRET`)는 애플리케이션 설정이 아니므로 허용 — 운영 자격증명을 넣지 않는다.

## 코드 리뷰 체크포인트

신규 PR 리뷰 시 본 룰 위반 여부를 우선 확인한다.

- [ ] 식별자·감사 주체 `Long`, 소유 DDL `BIGINT` — `Int`/`INT` 식별자 없음
- [ ] 업무 DDL 에 `FOREIGN KEY` 제약 없음 (논리 FK + `KEY`)
- [ ] 5계층 우회 없음, `@Scheduled` 없음
- [ ] 약어 없음, 메서드명 단수·복수 구분
- [ ] Model·DTO·JdbcEntity 는 data class + val, JPA Entity 는 class + var
- [ ] Model 모든 프로퍼티 @field:JsonProperty, 전부-기본값 data class 없음
- [ ] nullable 은 DB 컬럼 NULL 허용과 일치, !! 없음, 요청 Model 필수 필드 @field:NotNull 등 + @Valid
- [ ] MapStruct: 미매핑 타깃 없음(ERROR), non-null 타깃에 ignore 없음, is-prefix Boolean 은 @Mapping 명시
- [ ] JPA 연관 관계·Join 없음
- [ ] yml 의 비밀은 기본값 없는 kebab-case 논리 키 참조, 평문 자격증명 없음, 새 논리 키는 README·`.env.example`·`application-gcp.yml` 에 반영
- [ ] 클래스 레벨 `@Transactional(readOnly = true)`, 변경 메서드만 `@Transactional`, 전부 `transactionManager` 명시(primary 도), private/final·MappingService 에 없음, `transactionManagerRef` 빈 존재
- [ ] `./gradlew ktlintFormat` 통과
