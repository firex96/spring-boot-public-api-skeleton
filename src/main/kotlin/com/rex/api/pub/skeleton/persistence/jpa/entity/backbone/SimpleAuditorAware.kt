package com.rex.api.pub.skeleton.persistence.jpa.entity.backbone

import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import org.springframework.data.domain.AuditorAware
import java.util.Optional

/**
 * JPA Auditing 의 감사 주체를 공급한다.
 *
 * **[AuditorContext] 하나만 읽는다.** 그 값을 채우는 지점은 레포마다 다르다 —
 * 인증 수단이 다르기 때문이다.
 *
 * | 레포 | 채우는 곳 | 출처 |
 * |---|---|---|
 * | public | `SecurityAuditorContextFilter` | 인증 주체(JWT), 없으면 신뢰된 헤더 |
 * | internal · batch | `HeaderAuditorContextFilter` | 신뢰된 `X-User-Id` 헤더뿐 — **인증 프레임워크가 없다** |
 *
 * 비어 있으면 [Constant.SYSTEM_USER_ID] 가 감사 주체가 된다 — 실행 주체가
 * 사람이 아니라 시스템 자신이거나, **주체를 알 수 없다**는 뜻이다.
 *
 * ## MDC 를 쓰지 않는다
 *
 * 감사 주체는 한때 MDC 를 탔다. MDC 는 로깅 컨텍스트라 비어도 로그가 밋밋해질
 * 뿐이지만, **감사 주체가 비면 틀린 값이 DB 에 남는다** — 실패가 로그가 아니라
 * 데이터에 나타난다. 그래서 채널을 나눴다.
 *
 * ## ⚠ 신뢰 경계 — 파생 프로젝트가 반드시 판단할 것
 *
 * **`X-User-Id` 는 외부 입력이다.** 그대로 신뢰하면 헤더를 직접 넣을 수 있는
 * 호출자가 **다른 사용자의 ID 로 감사 기록을 위조**할 수 있다.
 *
 * **그래서 기본값이 꺼짐이다.** `app.audit.trust-user-header` 는 게이트웨이·
 * 프록시가 인증 결과로 헤더를 덮어쓰고 외부에서 들어온 같은 이름의 헤더는 제거하는
 * 구성에서만 켠다. **public 은 인증이 있으면 그것이 헤더를 이긴다.**
 *
 * internal·batch 는 인증 프레임워크가 없어 헤더 외의 선택지가 없다. 그래서 이 값을 켜는 배포는
 * 게이트웨이가 헤더를 덮어쓰는지를 배포 구성으로 보증해야 한다 — 코드만으로는 막을 수 없다.
 */
class SimpleAuditorAware : AuditorAware<Long> {
    override fun getCurrentAuditor(): Optional<Long> = Optional.of(AuditorContext.get() ?: Constant.SYSTEM_USER_ID)
}