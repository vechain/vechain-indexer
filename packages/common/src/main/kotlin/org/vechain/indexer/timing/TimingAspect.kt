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

    @Around(
        "execution(* org.vechain.indexer..*.processEvents(..)) || " +
            "execution(* org.vechain.indexer..*.save(..)) || " +
            "execution(* org.vechain.indexer..*.rollback(..)) || " +
            "execution(* org.vechain.indexer..*.findRecordsToPrune(..))"
    )
    fun logExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val targetClass = joinPoint.target?.javaClass
        val className = targetClass?.name ?: joinPoint.signature.declaringTypeName
        val methodName = joinPoint.signature.name
        var result: Any?
        val duration = measureTime { result = joinPoint.proceed() }
        val durationMs = duration.inWholeMilliseconds
        val callDescription = "$className.$methodName"
        when {
            verySlowThresholdMs > 0 && durationMs >= verySlowThresholdMs ->
                logger.error(
                    "⏱️ Very slow function call: $callDescription took $durationMs ms (threshold $verySlowThresholdMs ms)"
                )
            warnThresholdMs > 0 && durationMs >= warnThresholdMs ->
                logger.warn(
                    "⏱ Slow function call: $callDescription took $durationMs ms (threshold $warnThresholdMs ms)"
                )
            else -> logger.debug("⏱️ $callDescription took $duration")
        }
        return result
    }
}
