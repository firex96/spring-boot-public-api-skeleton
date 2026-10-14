package com.rex.api.pub.skeleton.aspect

import com.rex.api.pub.skeleton.annotation.SlowMethod
import com.rex.api.pub.skeleton.util.logger
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 메서드 실행 시간을 측정하고 임계값 초과 시 경고 로그를 출력하는 Aspect.
 */
@Aspect
@Component
@Order(1)
class SlowMethodAspect {
    @Around("@annotation(slowMethod)")
    @Throws(Throwable::class)
    fun checkExecutionTime(
        joinPoint: ProceedingJoinPoint,
        slowMethod: SlowMethod,
    ): Any? {
        val startNanos = System.nanoTime()
        val result: Any? = joinPoint.proceed(joinPoint.args)
        val elapsedNanos = System.nanoTime() - startNanos

        val thresholdNanos = TimeUnit.MICROSECONDS.toNanos(slowMethod.thresholdMicroSecond)
        if (elapsedNanos >= thresholdNanos) {
            logSlowMethod(joinPoint, slowMethod, elapsedNanos)
        }

        return result
    }

    private fun logSlowMethod(
        joinPoint: ProceedingJoinPoint,
        slowMethod: SlowMethod,
        elapsedNanos: Long,
    ) {
        val signature = joinPoint.signature as? MethodSignature
        val className = signature?.declaringType?.simpleName ?: joinPoint.target?.javaClass?.simpleName ?: "Unknown"
        val methodName = signature?.name ?: "unknown"

        val message = buildString {
            append("[SlowMethod] ")

            // TraceId가 있으면 포함
            MDC.get("traceId")?.let { traceId ->
                append("traceId=").append(traceId).append(" ")
            }

            append(className).append(".").append(methodName)
            append(" elapsed=").append(formatElapsedTime(elapsedNanos))

            // 인자 로깅 (옵션)
            if (slowMethod.logArguments && joinPoint.args.isNotEmpty()) {
                append(" args=[")
                joinPoint.args.forEachIndexed { index, arg ->
                    if (index > 0) append(", ")
                    append(safeToString(arg, slowMethod.maxArgLength))
                }
                append("]")
            }
        }

        logger.warn(message)
    }

    private fun formatElapsedTime(nanos: Long): String {
        val millis = nanos / 1_000_000.0
        return when {
            millis >= 1000 -> String.format("%.2fs", millis / 1000)
            millis >= 1 -> String.format("%.2fms", millis)
            else -> String.format("%.2fμs", nanos / 1000.0)
        }
    }

    private fun safeToString(
        obj: Any?,
        maxLength: Int,
    ): String =
        try {
            val str = obj?.toString() ?: "null"
            if (str.length > maxLength) {
                str.take(maxLength) + "..."
            } else {
                str
            }
        } catch (e: Exception) {
            "<error: ${e.javaClass.simpleName}>"
        }

    companion object {
        private val logger = logger()
    }
}