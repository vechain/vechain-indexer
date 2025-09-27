package org.vechain.indexer.timing

import kotlin.time.measureTime
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Aspect
@Component
class TimingAspect(
    @param:Value("\${timing.warn-threshold-ms}") private val warnThresholdMs: Long,
    @param:Value("\${timing.very-slow-threshold-ms}") private val verySlowThresholdMs: Long,
) {
    private val logger = LoggerFactory.getLogger(TimingAspect::class.java)

    @Around("@annotation(withTiming)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint, withTiming: WithTiming): Any? {
        val methodName = withTiming.value.ifEmpty { joinPoint.signature.toShortString() }
        var result: Any?
        val duration = measureTime { result = joinPoint.proceed() }
        val durationMs = duration.inWholeMilliseconds
        when {
            durationMs >= verySlowThresholdMs ->
                logger.error(
                    "⏱️ Very slow function call: $methodName took $durationMs ms (threshold $verySlowThresholdMs ms)"
                )
            durationMs >= warnThresholdMs ->
                logger.warn(
                    "⏱ Slow function call: $methodName took $durationMs ms (threshold $warnThresholdMs ms)"
                )
            else -> logger.debug("⏱️ $methodName took $duration")
        }
        return result
    }
}
