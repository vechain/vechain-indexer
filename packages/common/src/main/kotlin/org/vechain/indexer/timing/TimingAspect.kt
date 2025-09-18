package org.vechain.indexer.timing

import kotlin.time.measureTime
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class TimingAspect {
    private val logger = LoggerFactory.getLogger(TimingAspect::class.java)

    @Around("@annotation(withTiming)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint, withTiming: WithTiming): Any? {
        val methodName = withTiming.value.ifEmpty { joinPoint.signature.toShortString() }
        var result: Any?
        val duration = measureTime { result = joinPoint.proceed() }
        logger.info("⏱️ $methodName took ${duration.inWholeMilliseconds}ms ($duration)")
        return result
    }
}
