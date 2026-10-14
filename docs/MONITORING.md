# Monitoring Guide

> Public API Skeleton 모니터링, 로깅, 메트릭, 알림 설정 가이드

## 목차

- [개요](#개요)
- [로깅 (Logging)](#로깅-logging)
- [메트릭 (Metrics)](#메트릭-metrics)
- [헬스 체크 (Health Check)](#헬스-체크-health-check)
- [분산 추적 (Distributed Tracing)](#분산-추적-distributed-tracing)
- [알림 (Alerting)](#알림-alerting)
- [대시보드 (Dashboard)](#대시보드-dashboard)

---

## 개요

### 모니터링 스택

| 구성 요소 | 권장 도구 | 용도 |
|-----------|-----------|------|
| 로깅 | Loki + Grafana | 로그 수집 및 검색 |
| 메트릭 | Prometheus + Grafana | 시계열 메트릭 수집 |
| 추적 | Jaeger / Zipkin | 분산 추적 |
| 알림 | Alertmanager / PagerDuty | 알림 발송 |
| APM | 클라우드 APM (CloudWatch · Azure Monitor · Cloud Monitoring 등) | 전체 애플리케이션 성능 |

### Spring Boot Actuator 활성화

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## 로깅 (Logging)

### 로깅 설정

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.rex: DEBUG
    org.springframework: WARN
    org.hibernate.SQL: DEBUG  # SQL 로깅 (개발 환경)
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/app/application.log
    max-size: 100MB
    max-history: 30
```

### 구조화된 로깅 (JSON)

```yaml
# 운영 설정 (환경변수 LOGGING_PATTERN_CONSOLE 로도 줄 수 있다)
logging:
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%level","traceId":"%X{traceId:-}","logger":"%logger{36}","message":"%msg"}%n'
```

### 로거 사용

```kotlin
import com.rex.api.pub.skeleton.util.logger

class MyService {
    companion object {
        private val logger = logger()
    }

    fun doSomething() {
        logger.info("작업 시작")
        logger.debug("상세 정보: {}", details)
        logger.warn("주의 필요: {}", warning)
        logger.error("오류 발생", exception)
    }
}
```

### MDC (Mapped Diagnostic Context)

```kotlin
// 자동으로 추가되는 MDC 키
// - traceId: 요청 추적 ID
// - userId: 인증된 사용자 ID
// - requestUri: 요청 URI

// 커스텀 MDC 추가
MDC.put("orderId", orderId)
try {
    // 비즈니스 로직
} finally {
    MDC.remove("orderId")
}
```

### Loki 통합

Loki 전송은 **`loki` 프로필을 조합하면** 켜진다(예: `SPRING_PROFILES_ACTIVE=aws,loki`). 주소는 환경변수 `LOKI_URL` 로 받는다(없으면 `http://localhost:3100/loki/api/v1/push`). 설정은 `src/main/resources/logback-spring.xml` 하나에 `<springProfile name="loki">` 로 들어 있다 — 라벨의 `APP_NAME` 은 `spring.application.name` 을 `<springProperty>` 로 끌어온 값, `HOSTNAME` 은 logback 컨텍스트 변수다.

```xml
<!-- logback-spring.xml -->
<springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="public-api-skeleton"/>
<springProfile name="loki">
  <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
      <url>${LOKI_URL:-http://localhost:3100/loki/api/v1/push}</url>
    </http>
    <format>
      <label>
        <pattern>
          app = ${APP_NAME},
          host = ${HOSTNAME}
        </pattern>
        <structuredMetadataPattern>
          level = %level,
          thread = %thread,
          class = %logger,
          traceId = %mdc{traceId:-none}
        </structuredMetadataPattern>
      </label>
      <message>
        <pattern>%-5level %logger{20} %msg %ex</pattern>
      </message>
    </format>
  </appender>
</springProfile>
```

### 로그 레벨 동적 변경

```bash
# 현재 로그 레벨 확인
curl http://localhost:8080/actuator/loggers/com.rex

# 로그 레벨 변경
curl -X POST http://localhost:8080/actuator/loggers/com.rex \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## 메트릭 (Metrics)

### Prometheus 설정

```yaml
# application.yml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms, 500ms, 1s, 5s
```

### 커스텀 메트릭

```kotlin
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

@Service
class AuthBizService(
    private val meterRegistry: MeterRegistry,
) {
    // 카운터 메트릭
    private val loginCounter = Counter.builder("auth.login")
        .description("로그인 시도 횟수")
        .register(meterRegistry)

    private val loginFailureCounter = Counter.builder("auth.login.failure")
        .description("로그인 실패 횟수")
        .register(meterRegistry)

    // 타이머 메트릭
    private val loginTimer = Timer.builder("auth.login.duration")
        .description("로그인 처리 시간")
        .register(meterRegistry)

    fun login(request: LoginRequest) {
        loginCounter.increment()

        loginTimer.record {
            try {
                // 로그인 로직
            } catch (e: Exception) {
                loginFailureCounter.increment()
                throw e
            }
        }
    }
}
```

### 게이지 메트릭

```kotlin
@Component
class SessionMetrics(
    meterRegistry: MeterRegistry,
    private val sessionRepository: UserSessionRepository,
) {
    init {
        Gauge.builder("sessions.active") { sessionRepository.countActiveSessions() }
            .description("활성 세션 수")
            .register(meterRegistry)
    }
}
```

### 주요 메트릭

| 메트릭 | 설명 | 유형 |
|--------|------|------|
| `http.server.requests` | HTTP 요청 | Timer |
| `jvm.memory.used` | JVM 메모리 사용량 | Gauge |
| `jvm.gc.pause` | GC 일시 정지 | Timer |
| `hikaricp.connections.active` | 활성 DB 연결 | Gauge |
| `auth.login` | 로그인 횟수 | Counter |
| `auth.login.failure` | 로그인 실패 횟수 | Counter |
| `sessions.active` | 활성 세션 수 | Gauge |

### Prometheus 스크래핑 설정

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'public-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-server:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
```

---

## 헬스 체크 (Health Check)

### 헬스 엔드포인트

```yaml
# application.yml
management:
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true  # 로드밸런서 헬스 프로브 (liveness/readiness 엔드포인트)
  health:
    db:
      enabled: true
    redis:
      enabled: true
    diskspace:
      enabled: true
```

### 커스텀 헬스 인디케이터

```kotlin
@Component
class ExternalServiceHealthIndicator(
    private val externalClient: ExternalApiClient,
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val status = externalClient.healthCheck()
            if (status.isHealthy) {
                Health.up()
                    .withDetail("service", "external-api")
                    .withDetail("responseTime", status.responseTime)
                    .build()
            } else {
                Health.down()
                    .withDetail("error", status.errorMessage)
                    .build()
            }
        } catch (e: Exception) {
            Health.down(e).build()
        }
    }
}
```

### 로드 밸런서·오케스트레이터 헬스 체크

로드 밸런서(또는 Kubernetes readiness/liveness 프로브)는 아래 두 엔드포인트를 쓴다. 배포 방식(VM·컨테이너·Kubernetes)은 파생 프로젝트가 정한다.

| 용도 | 경로 | 권장 주기 |
|---|---|---|
| 기동 완료·트래픽 수용 | `GET /actuator/health/readiness` | 5초, 실패 3회 후 제외 |
| 프로세스 생존 | `GET /actuator/health/liveness` | 10초, 실패 3회 후 재시작(systemd `Restart=on-failure` 또는 Kubernetes liveness) |

### 헬스 체크 응답 예시

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "SELECT 1"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 107374182400,
        "free": 85899345920,
        "threshold": 10485760
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    }
  }
}
```

---

## 분산 추적 (Distributed Tracing)

### Micrometer Tracing 설정

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 개발환경: 100%, 프로덕션: 0.1 (10%)
    propagation:
      type: w3c  # 또는 b3

spring:
  application:
    name: public-api
```

### 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
}
```

### Jaeger 연동

```yaml
# application.yml
management:
  otlp:
    tracing:
      endpoint: http://jaeger:4318/v1/traces
```

### 커스텀 스팬

```kotlin
import io.micrometer.tracing.Tracer

@Service
class PaymentService(
    private val tracer: Tracer,
) {
    fun processPayment(orderId: String): PaymentResult {
        val span = tracer.nextSpan()
            .name("process-payment")
            .tag("orderId", orderId)
            .start()

        try {
            tracer.currentTraceContext().newScope(span.context()).use {
                // 결제 처리 로직
                return PaymentResult.success()
            }
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            span.end()
        }
    }
}
```

### 트레이스 ID 전파

```kotlin
// HTTP 헤더로 트레이스 ID 전파
// X-Trace-Id: abc123def456

// 로그에서 확인
// 2024-01-15 10:30:00.000 [http-nio-8080-exec-1] [abc123def456] INFO ...
```

---

## 알림 (Alerting)

### Prometheus Alerting Rules

```yaml
# alert-rules.yml
groups:
  - name: public-api-alerts
    rules:
      # 높은 에러율
      - alert: HighErrorRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count[5m]))
          ) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "높은 에러율 감지"
          description: "5분간 에러율이 5%를 초과했습니다."

      # 느린 응답 시간
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
          > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "높은 응답 지연"
          description: "P95 응답 시간이 1초를 초과했습니다."

      # DB 연결 풀 고갈
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "DB 연결 풀 부족"
          description: "DB 연결 풀 사용률이 90%를 초과했습니다."

      # JVM 메모리 부족
      - alert: HighMemoryUsage
        expr: |
          jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM 힙 메모리 부족"
          description: "JVM 힙 메모리 사용률이 90%를 초과했습니다."

      # 로그인 실패 급증
      - alert: HighLoginFailureRate
        expr: |
          rate(auth_login_failure_total[5m]) > 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "로그인 실패 급증"
          description: "분당 로그인 실패가 10회를 초과했습니다. 공격 가능성 검토 필요."

      # 활성 세션 급감
      - alert: ActiveSessionsDrop
        expr: |
          (sessions_active - sessions_active offset 5m) / sessions_active offset 5m < -0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "활성 세션 급감"
          description: "5분간 활성 세션이 50% 이상 감소했습니다."
```

### Alertmanager 설정

```yaml
# alertmanager.yml
global:
  slack_api_url: 'https://hooks.slack.com/services/xxx'

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'default'
    slack_configs:
      - channel: '#alerts'
        send_resolved: true

  - name: 'slack'
    slack_configs:
      - channel: '#alerts'
        send_resolved: true
        title: '{{ .CommonAnnotations.summary }}'
        text: '{{ .CommonAnnotations.description }}'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'xxx'
        severity: '{{ .CommonLabels.severity }}'
```

### 슬랙 알림 예시

```
🚨 [CRITICAL] 높은 에러율 감지
5분간 에러율이 5%를 초과했습니다.

📊 세부 정보:
- 현재 에러율: 7.2%
- 영향받는 서비스: public-api
- 시작 시간: 2024-01-15 10:30:00

🔗 대시보드: [링크]
```

---

## 대시보드 (Dashboard)

### Grafana 대시보드 구성

#### 1. 애플리케이션 개요

| 패널 | 쿼리 | 설명 |
|------|------|------|
| 요청 수 | `sum(rate(http_server_requests_seconds_count[5m]))` | 초당 요청 수 |
| 에러율 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | 에러 비율 |
| P95 응답시간 | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` | 95퍼센타일 응답시간 |
| 활성 세션 | `sessions_active` | 현재 활성 세션 수 |

#### 2. JVM 메트릭

```promql
# 힙 메모리 사용량
jvm_memory_used_bytes{area="heap"}

# GC 일시정지 시간
rate(jvm_gc_pause_seconds_sum[5m])

# 스레드 수
jvm_threads_live_threads

# CPU 사용량
process_cpu_usage
```

#### 3. 데이터베이스 메트릭

```promql
# 활성 연결 수
hikaricp_connections_active

# 대기 연결 수
hikaricp_connections_pending

# 연결 획득 시간
hikaricp_connections_acquire_seconds
```

#### 4. 인증 메트릭

```promql
# 로그인 성공률
rate(auth_login_total[5m]) - rate(auth_login_failure_total[5m])

# 로그인 실패율
rate(auth_login_failure_total[5m])

# 토큰 갱신 수
rate(auth_token_refresh_total[5m])
```

### Grafana 대시보드 JSON

```json
{
  "dashboard": {
    "title": "Public API Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count[5m]))",
            "legendFormat": "Requests/sec"
          }
        ]
      },
      {
        "title": "Error Rate",
        "type": "gauge",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count{status=~'5..'}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100"
          }
        ],
        "thresholds": {
          "mode": "absolute",
          "steps": [
            {"value": 0, "color": "green"},
            {"value": 1, "color": "yellow"},
            {"value": 5, "color": "red"}
          ]
        }
      }
    ]
  }
}
```

---

## 운영 체크리스트

### 배포 전 확인사항

- [ ] 로그 레벨 설정 확인 (프로덕션: INFO)
- [ ] 샘플링 비율 설정 (트레이싱: 10%)
- [ ] 알림 수신자 설정
- [ ] 대시보드 접근 권한 설정
- [ ] 로그 보관 정책 설정

### 일일 모니터링 항목

- [ ] 에러율 확인
- [ ] 응답 시간 확인
- [ ] 메모리/CPU 사용량 확인
- [ ] 활성 세션 수 확인
- [ ] 알림 이력 검토

### 주간 리뷰 항목

- [ ] 성능 트렌드 분석
- [ ] 용량 계획 검토
- [ ] 알림 규칙 튜닝
- [ ] 로그 패턴 분석

---

## 참고

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/)
- [Grafana](https://grafana.com/)
- [Prometheus](https://prometheus.io/)
- [Troubleshooting](TROUBLESHOOTING.md) - 성능 문제 해결
