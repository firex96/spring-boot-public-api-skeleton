package com.rex.api.pub.skeleton.config.audit

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 감사(Audit) 관련 설정.
 */
@ConfigurationProperties(prefix = "app.audit")
data class AuditProperties(
    /**
     * 인바운드 `X-User-Id` 헤더를 감사 주체로 신뢰할지.
     *
     * **기본값 `false` 다.** 헤더는 외부 입력이라 그대로 신뢰하면 호출자가
     * **다른 사용자의 ID 로 감사 기록을 위조**할 수 있다.
     *
     * 게이트웨이·프록시가 인증 결과로 이 헤더를 덮어쓰고 **외부에서 들어온 같은 이름의
     * 헤더를 제거**하는 배치에서만 켠다. 그 전제가 없으면 켜지 않는다.
     *
     * 꺼져 있으면 감사 주체는 `Constant.SYSTEM_USER_ID` 가 된다 — 정확도를 잃는 대신
     * 위조가 불가능하다.
     */
    val trustUserHeader: Boolean = false,
)