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

OAuth2 프로토콜 엔드포인트를 뺀 모든 에러 응답(필터 체인의 401·403, 메서드 보안 거부, CSRF 거부, 업무 예외)은 `ErrorResponseModel` 형식을 따릅니다:

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

OAuth2 프로토콜 엔드포인트(`/oauth2/token`·`introspect`·`revoke`, 그리고 `/oauth2/authorize` 의 리다이렉트 전 오류)는 RFC 6749 §5.2 형식을 따릅니다:

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
  "resultMsg": "Missing Servlet RequestParameter Exception"
}
```

**해결 방법**:
- 필수 쿼리 파라미터를 포함하여 다시 요청

---

### G005 - I/O Error

**HTTP Status**: 400

입출력 오류입니다.

**원인**:
- 파일 업로드 실패
- 네트워크 연결 문제

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
  "status": 400,
  "divisionCode": "G011",
  "resultMsg": "email: 올바른 형식의 이메일 주소여야 합니다, password: 비밀번호는 8자 이상이어야 합니다"
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

### G999 - Internal Server Error

**HTTP Status**: 500

서버 내부 오류입니다.

**원인**:
- 예상치 못한 서버 오류
- 외부 서비스 연결 실패

**해결 방법**:
- 잠시 후 재시도
- 문제 지속 시 서버 관리자에게 문의

---

## 인증 에러

### AuthenticationException

**HTTP Status**: 401

인증에 실패했습니다.

**원인**:
- 잘못된 아이디 또는 비밀번호
- 만료된 토큰

**응답 예시**:
```json
{
  "status": 401,
  "divisionCode": "G008",
  "resultMsg": "아이디 또는 비밀번호가 올바르지 않습니다"
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

### 9999 - Transaction Error

**HTTP Status**: 200 (응답 본문에 에러 정보 포함)

트랜잭션 처리 중 오류가 발생했습니다.

**종류**:
- INSERT_ERROR: 데이터 삽입 실패
- UPDATE_ERROR: 데이터 수정 실패
- DELETE_ERROR: 데이터 삭제 실패

---

## 클라이언트 처리 가이드

### JavaScript/TypeScript 예시

```typescript
interface ErrorResponse {
  status: number;
  divisionCode: string;
  resultMsg: string;
}

async function handleApiError(response: Response) {
  if (!response.ok) {
    const error: ErrorResponse = await response.json();

    switch (response.status) {
      case 401:
        // 인증 실패 - 로그인 페이지로 이동 또는 토큰 갱신
        if (error.resultMsg.includes('만료')) {
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
        showError(error.resultMsg);
        break;

      case 400:
        // 유효성 검증 실패 - 필드별 에러 표시
        if (error.divisionCode === 'G011') {
          parseValidationErrors(error.resultMsg);
        } else {
          showError(error.resultMsg);
        }
        break;

      default:
        showError('오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
    }

    throw new Error(error.resultMsg);
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
