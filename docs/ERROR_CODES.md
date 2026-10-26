# Error Codes Reference

> Public API Skeleton의 모든 에러 코드 및 HTTP 상태 코드 정리

## 목차

- [에러 응답 형식](#에러-응답-형식)
- [HTTP 상태 코드](#http-상태-코드)
- [글로벌 에러 코드](#글로벌-에러-코드)
- [인증 에러](#인증-에러)
- [OAuth2 에러](#oauth2-에러)
- [비즈니스 에러](#비즈니스-에러)
- [클라이언트 처리 가이드](#클라이언트-처리-가이드)

---

## 에러 응답 형식

OAuth2 프로토콜 엔드포인트와 인가 서버 브라우저 화면(`/oauth2/login`·`/oauth2/consent` 의 검증 실패는 HTML 오류 화면 `oauth2/error`, `invalid_client` 401·그 외 400)을 뺀 모든 에러 응답(필터 체인의 401·403, 메서드 보안 거부, CSRF 거부, 업무 예외)은 `ErrorResponseModel` 형식을 따릅니다:

```json
{
  "code": "FORBIDDEN_ERROR",
  "reason": "접근 권한이 없습니다",
  "errors": null,
  "status": 403,
  "divisionCode": "G008",
  "resultMessage": "Forbidden Exception"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| code | string | `ErrorCode` 이름 |
| reason | string? | 상세 사유 |
| errors | array? | 필드 검증 오류 (`field`, `value`, `reason`) |
| status | int | HTTP 상태 코드 |
| divisionCode | string | 에러 구분 코드 |
| resultMessage | string | 에러 메시지 |

필터 체인 단계(인증 없음 401, URL 인가 거부 403)는 `SecurityErrorResponseWriter` 가, 컨트롤러 이후 단계는 `GlobalExceptionHandler` 가 같은 모델로 씁니다. 인증 없이 메서드 보안에 거부되면 403 이 아니라 401 입니다.

### OAuth2 에러 응답

OAuth2 프로토콜 엔드포인트(`/oauth2/token`·`introspect`·`revoke`·`userinfo`, 그리고 `/oauth2/authorize` 의 리다이렉트 전 오류)는 RFC 6749 §5.2 형식을 따릅니다:

```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri가 일치하지 않습니다",
  "error_uri": "https://docs.example.com/errors/invalid_request"
}
```

---

## HTTP 상태 코드

| 코드 | 이름 | 설명 |
|------|------|------|
| 200 | OK | 요청 성공 |
| 201 | Created | 리소스 생성 성공 |
| 204 | No Content | 요청 성공 (응답 본문 없음) |
| 400 | Bad Request | 잘못된 요청 (유효성 검증 실패) |
| 401 | Unauthorized | 인증 필요 또는 인증 실패 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 409 | Conflict | 리소스 충돌 (중복 등) |
| 423 | Locked | 계정 잠금 |
| 429 | Too Many Requests | 요청 횟수 초과 |
| 500 | Internal Server Error | 서버 내부 오류 |
| 502 | Bad Gateway | 외부 API 가 오류로 응답했거나 호출이 실패함 |
| 503 | Service Unavailable | 외부 API 에 연결하지 못함 |

응답 본문의 `status` 는 언제나 HTTP 상태와 같다. 둘 다 `ErrorCode` 의 status 하나에서 나온다.

---

## 글로벌 에러 코드

### G001 - Bad Request

**HTTP Status**: 400

잘못된 요청입니다.

**원인**:
- 요청 본문 형식 오류
- 지원하지 않는 미디어 타입

**해결 방법**:
- 요청 본문이 올바른 JSON 형식인지 확인
- Content-Type 헤더가 `application/json`인지 확인

---

### G002 - Request Body Missing

**HTTP Status**: 400

요청 본문이 없습니다.

**원인**:
- POST/PUT 요청 시 본문 누락
- JSON 파싱 실패

**해결 방법**:
- 요청 본문을 포함하여 다시 요청
- JSON 구문이 올바른지 확인

---

### G003 - Invalid Type Value

**HTTP Status**: 400

유효하지 않은 타입 값입니다.

**원인**:
- 숫자 필드에 문자열 전송
- enum 필드에 잘못된 값 전송

**해결 방법**:
- API 문서를 확인하여 올바른 타입으로 전송

---

### G004 - Missing Request Parameter

**HTTP Status**: 400

필수 요청 파라미터가 누락되었습니다.

**응답 예시**:
```json
{
  "status": 400,
  "divisionCode": "G004",
  "resultMessage": "Missing Servlet RequestParameter Exception"
}
```

**해결 방법**:
- 필수 쿼리 파라미터를 포함하여 다시 요청

---

### G005 - I/O Error

**HTTP Status**: 500

서버 측 입출력 오류입니다.

**원인**:
- 파일 읽기·쓰기 실패
- 서버 내부 네트워크 입출력 문제

---

### G006 - JSON Parse Error

**HTTP Status**: 400

JSON 파싱에 실패했습니다.

**원인**:
- 잘못된 JSON 구문
- 인코딩 문제

**해결 방법**:
- JSON 유효성 검사 후 재요청
- UTF-8 인코딩 사용

---

### G007 - Jackson Processing Error

**HTTP Status**: 400

JSON 직렬화/역직렬화 오류입니다.

**원인**:
- 알 수 없는 프로퍼티
- 타입 변환 실패

---

### G008 - Forbidden

**HTTP Status**: 403

접근 권한이 없습니다.

**원인**:
- 해당 리소스에 대한 권한 부족
- 관리자 전용 API 접근 시도

**해결 방법**:
- 적절한 권한을 가진 계정으로 재시도
- 관리자에게 권한 요청

---

### G009 - Not Found

**HTTP Status**: 404

요청한 리소스를 찾을 수 없습니다.

**원인**:
- 존재하지 않는 URL
- 삭제된 리소스

**해결 방법**:
- URL 경로 확인
- 리소스 존재 여부 확인

---

### G010 - Null Pointer Error

**HTTP Status**: 500

서버 내부 오류 (Null 참조)입니다.

**원인**:
- 서버 측 로직 오류

**해결 방법**:
- 서버 관리자에게 문의

---

### G011 - Validation Error

**HTTP Status**: 400

유효성 검증에 실패했습니다.

**응답 예시**:
```json
{
  "code": "NOT_VALID_ERROR",
  "reason": "email: 올바른 형식의 이메일 주소여야 합니다, password: 비밀번호는 8자 이상이어야 합니다",
  "errors": null,
  "status": 400,
  "divisionCode": "G011",
  "resultMessage": "handle Validation Exception"
}
```

**해결 방법**:
- 응답 메시지에서 검증 실패 필드 확인
- 해당 필드 값 수정 후 재요청

---

### G012 - Header Missing Error

**HTTP Status**: 400

필수 헤더가 누락되었습니다.

**원인**:
- Authorization 헤더 누락
- X-User-Id 헤더 누락

**해결 방법**:
- 필수 헤더 포함하여 재요청

---

### G014 - External API Error

**HTTP Status**: 502

외부 API 가 오류로 응답했거나 호출이 실패했습니다. `ApiCallException` 의 기본 코드입니다.

**해결 방법**:
- 잠시 후 재시도
- 문제 지속 시 서버 관리자에게 문의

---

### G016 - Service Unavailable

**HTTP Status**: 503

외부 API 에 연결하지 못했습니다(연결 거부·타임아웃·DNS). `WebClientRequestException`, 그리고 `ApiCallException(errorCode = SERVICE_UNAVAILABLE_ERROR)` 가 이 코드로 응답합니다.

**해결 방법**:
- 잠시 후 재시도

---

### G999 - Internal Server Error

**HTTP Status**: 500

서버 내부 오류입니다.

**원인**:
- 예상치 못한 서버 오류

**해결 방법**:
- 잠시 후 재시도
- 문제 지속 시 서버 관리자에게 문의

---

## 인증 에러

인증 예외는 모두 `AuthException`(→ `CodedException`)의 하위 타입이고, 자기 `ErrorCode` 만 정한다. 응답은 `GlobalExceptionHandler` 의 `CodedException` 핸들러 하나가 만든다.

| 예외 | ErrorCode | HTTP | 구분 코드 |
|---|---|---|---|
| `AuthenticationException` · `UnauthorizedException` · `InvalidTokenException` | `UNAUTHORIZED_ERROR` | 401 | G007A |
| `ForbiddenException` · `AuthorizationException` | `FORBIDDEN_ERROR` | 403 | G008 |
| `DuplicateUserException` · `DuplicateResourceException` | `CONFLICT_ERROR` | 409 | G008A |
| `ResourceNotFoundException` | `NOT_FOUND_ERROR` | 404 | G009 |
| `AccountLockedException` | `ACCOUNT_LOCKED_ERROR` | 423 | AUTH001 |
| `MfaException` | 생성자 인자 `MFA_*` | 아래 표 | MFA001~MFA006 |

### AuthenticationException

**HTTP Status**: 401

인증에 실패했습니다.

**원인**:
- 잘못된 아이디 또는 비밀번호
- 만료된 토큰

**응답 예시**:
```json
{
  "code": "UNAUTHORIZED_ERROR",
  "reason": "아이디 또는 비밀번호가 올바르지 않습니다",
  "errors": null,
  "status": 401,
  "divisionCode": "G007A",
  "resultMessage": "Unauthorized Exception"
}
```

---

### InvalidTokenException

**HTTP Status**: 401

유효하지 않은 토큰입니다.

**원인**:
- 변조된 토큰
- 잘못된 서명
- 만료된 토큰

**해결 방법**:
- 새로운 토큰 발급 (refresh token 사용)
- 재로그인

---

### UnauthorizedException

**HTTP Status**: 401

인증이 필요합니다.

**원인**:
- Authorization 헤더 누락
- 토큰 형식 오류

**해결 방법**:
- `Authorization: Bearer {token}` 헤더 추가

---

### ForbiddenException

**HTTP Status**: 403

접근이 거부되었습니다.

**원인**:
- 필요한 권한 부족
- 리소스 소유자가 아님

---

### DuplicateUserException

**HTTP Status**: 409

이미 존재하는 사용자입니다.

**원인**:
- 중복된 loginId로 회원가입 시도
- 이미 연동된 소셜 계정

---

### ResourceNotFoundException

**HTTP Status**: 404

리소스를 찾을 수 없습니다.

**원인**:
- 존재하지 않는 사용자 ID
- 삭제된 세션

---

### AccountLockedException

**HTTP Status**: 423

계정이 잠겼습니다.

**원인**:
- 로그인 시도 횟수 초과

**응답 예시**:
```json
{
  "status": 423,
  "divisionCode": "AUTH001",
  "reason": "계정이 잠겼습니다. 1799초 후 다시 시도하세요.",
  "resultMessage": "Account Locked Exception"
}
```

**해결 방법**:
- 잠금 시간이 지난 후 재시도
- 관리자에게 잠금 해제 요청

---

### MfaException

2차 인증(TOTP) 실패. 상태와 구분 코드는 사유마다 다르다(`ErrorCode.MFA_*`). 본문은 `ErrorResponseModel` 이다.

| 구분 코드 | HTTP | 사유 |
|---|---|---|
| `MFA001` | 400 | 코드가 틀렸다(같은 구간의 TOTP·이미 쓴 복구 코드를 다시 낸 경우 포함). 로그인 2단계면 계정 잠금 카운터가 오른다 |
| `MFA002` | 401 | challenge 가 없다 — 만료(5분)·이미 사용·실패 한도(5회) 초과·다른 완료 경로의 challenge. 다시 로그인한다 |
| `MFA003` | 409 | 이미 켜져 있다(setup·activate) |
| `MFA004` | 409 | 꺼져 있다(disable) |
| `MFA005` | 404 | 등록을 시작하지 않았다(setup 없이 activate) |
| `MFA006` | 429 | 등록·관리 API 의 코드를 5번 틀렸다 — 15분 동안 막는다(로그인 잠금과 별개) |

**응답 예시**:
```json
{
  "status": 400,
  "divisionCode": "MFA001",
  "reason": "코드가 올바르지 않습니다",
  "resultMessage": "Invalid MFA Code"
}
```

로그인 2단계에서 실패 누적으로 계정이 잠기면 `MFA001` 대신 `AccountLockedException`(423, `AUTH001`)이다.

---

## OAuth2 에러

OAuth2 에러는 RFC 6749 표준 에러 코드를 사용합니다.

### invalid_request

**HTTP Status**: 400

요청이 유효하지 않습니다.

**원인**:
- 필수 파라미터 누락
- 지원하지 않는 파라미터 포함
- 파라미터 중복

**응답 예시**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri는 필수입니다"
}
```

---

### invalid_client

**HTTP Status**: 401

클라이언트 인증에 실패했습니다.

**원인**:
- 존재하지 않는 client_id
- 잘못된 client_secret
- 비활성화된 클라이언트

**응답 예시**:
```json
{
  "error": "invalid_client",
  "error_description": "클라이언트 인증에 실패했습니다"
}
```

---

### invalid_grant

**HTTP Status**: 400

인가 승인(grant)이 유효하지 않습니다.

**원인**:
- 만료된 authorization code
- 이미 사용된 authorization code
- 잘못된 refresh token

---

### unauthorized_client

**HTTP Status**: 400

클라이언트가 이 grant type을 사용할 권한이 없습니다.

**원인**:
- 허용되지 않은 grant_type 사용

---

### unsupported_grant_type

**HTTP Status**: 400

지원하지 않는 grant type입니다.

**원인**:
- 인식할 수 없는 grant_type 값

**지원하는 grant_type**:
- `authorization_code`
- `refresh_token`
- `client_credentials`

---

### invalid_scope

**HTTP Status**: 400

요청한 scope가 유효하지 않습니다.

**원인**:
- 알 수 없는 scope 값
- 클라이언트에게 허용되지 않은 scope

---

### access_denied

**HTTP Status**: 403

리소스 소유자 또는 인가 서버가 요청을 거부했습니다.

**원인**:
- 사용자가 동의를 거부함
- 접근 권한 없음

---

### server_error

**HTTP Status**: 500

인가 서버에서 예상치 못한 오류가 발생했습니다.

---

### temporarily_unavailable

**HTTP Status**: 503

인가 서버가 일시적으로 과부하 상태입니다.

---

## 비즈니스 에러

업무 오류 코드는 `ErrorCode` 의 「Custom Error CodeList」 자리에 추가한다. 스켈레톤은 업무 코드를 두지 않는다.

### 새 에러 코드 추가

1. `enums/ErrorCode.kt` 에 항목을 추가한다 — `ORDER_ALREADY_PAID(409, "B001", "Order Already Paid")`. status 가 곧 HTTP 응답 상태이고 400~599 여야 한다. 구분 코드는 다른 항목과 겹치면 안 된다(`ErrorCodeContractTest` 가 검사한다).
2. 던지는 곳에서 `throw CodedException(ErrorCode.ORDER_ALREADY_PAID, "이미 결제된 주문입니다")` 로 던진다. 메시지는 응답 `reason` 으로 나가므로 사용자에게 보여도 되는 문장으로 쓴다.
3. 전용 예외 클래스는 같은 코드를 여러 곳에서 던지거나 `catch` 로 구분해야 할 때만 `CodedException` 을 상속해 만든다. 전용 핸들러는 만들지 않는다 — 응답 본문 모양이나 노출 정책이 다를 때만 예외다.
4. 이 문서의 코드 표에 항목을 추가한다.

---

## 클라이언트 처리 가이드

### JavaScript/TypeScript 예시

```typescript
interface ErrorResponse {
  status: number;
  divisionCode: string;
  code: string;
  reason: string | null; // 상세 사유 (예외 메시지)
  resultMessage: string; // 코드의 고정 문구
}

async function handleApiError(response: Response) {
  if (!response.ok) {
    const error: ErrorResponse = await response.json();

    switch (response.status) {
      case 401:
        // 인증 실패 - 로그인 페이지로 이동 또는 토큰 갱신
        if (error.reason?.includes('만료')) {
          await refreshToken();
          return retry(originalRequest);
        }
        redirectToLogin();
        break;

      case 403:
        // 권한 없음 - 에러 메시지 표시
        showError('접근 권한이 없습니다');
        break;

      case 404:
        // 리소스 없음
        showError('요청한 정보를 찾을 수 없습니다');
        break;

      case 423:
        // 계정 잠금
        showError(error.reason ?? error.resultMessage);
        break;

      case 400:
        // 유효성 검증 실패 - 필드별 에러 표시
        if (error.divisionCode === 'G011') {
          parseValidationErrors(error.reason ?? '');
        } else {
          showError(error.reason ?? error.resultMessage);
        }
        break;

      default:
        showError('오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
    }

    throw new Error(error.reason ?? error.resultMessage);
  }
}
```

### 재시도 전략

```typescript
async function apiCallWithRetry(
  request: () => Promise<Response>,
  maxRetries: number = 3
): Promise<Response> {
  let lastError: Error;

  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await request();

      if (response.status === 429) {
        // Rate limit - 지수 백오프로 재시도
        const delay = Math.pow(2, i) * 1000;
        await sleep(delay);
        continue;
      }

      if (response.status >= 500) {
        // 서버 에러 - 재시도
        await sleep(1000);
        continue;
      }

      return response;
    } catch (e) {
      lastError = e as Error;
      if (i < maxRetries - 1) {
        await sleep(1000);
      }
    }
  }

  throw lastError;
}
```

### 토큰 갱신 처리

```typescript
let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

async function refreshTokenAndRetry(failedRequest: Request): Promise<Response> {
  if (!isRefreshing) {
    isRefreshing = true;

    try {
      const newToken = await refreshAccessToken();
      refreshSubscribers.forEach(callback => callback(newToken));
      refreshSubscribers = [];
    } finally {
      isRefreshing = false;
    }
  }

  return new Promise(resolve => {
    refreshSubscribers.push((token: string) => {
      const newRequest = failedRequest.clone();
      newRequest.headers.set('Authorization', `Bearer ${token}`);
      resolve(fetch(newRequest));
    });
  });
}
```

---

## 참고

- [API Reference](API_REFERENCE.md) - API 엔드포인트 상세
- [시퀀스 다이어그램](SEQUENCE_DIAGRAMS.md) - 상세 API 흐름
- [RFC 6749 - OAuth 2.0](https://tools.ietf.org/html/rfc6749) - OAuth2 표준
