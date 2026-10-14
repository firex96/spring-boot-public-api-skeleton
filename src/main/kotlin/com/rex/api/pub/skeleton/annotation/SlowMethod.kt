package com.rex.api.pub.skeleton.annotation

/**
 * 메서드 실행 시간이 임계값을 초과하면 경고 로그를 출력하는 어노테이션.
 *
 * @param thresholdMicroSecond 임계값 (마이크로초 단위). 기본값: 100ms (100,000μs)
 * @param logArguments 인자 값을 로그에 포함할지 여부. 민감 정보가 있으면 false로 설정
 * @param maxArgLength 로그에 출력할 각 인자의 최대 길이. 초과 시 잘림
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SlowMethod(
    val thresholdMicroSecond: Long = 100_000L,
    val logArguments: Boolean = true,
    val maxArgLength: Int = 100,
)