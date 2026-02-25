package org.vechain.indexer.config.mongo

import com.mongodb.MongoException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.vechain.indexer.config.metrics.TransactionRetryMetrics

/**
 * Retries methods annotated with @Transactional when MongoDB raises a TransientTransactionError.
 *
 * This aspect is ordered to wrap OUTSIDE the transaction interceptor, so each retry starts a fresh
 * transaction. MongoDB Atlas aborts transactions that exceed the server-side lifetime limit (60s),
 * returning a TransientTransactionError label that signals the client should retry.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
open class TransientTransactionRetryAspect(private val retryMetrics: TransactionRetryMetrics) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    fun retryOnTransientTransactionError(joinPoint: ProceedingJoinPoint): Any? {
        var lastException: Exception? = null
        val targetClass = joinPoint.signature.declaringType.simpleName

        for (attempt in 1..MAX_RETRIES) {
            try {
                return joinPoint.proceed()
            } catch (e: Exception) { // Intentionally Exception, not Throwable: Errors (OOM, etc.)
                // should not be retried.
                if (!isTransientTransactionError(e)) {
                    throw e
                }

                retryMetrics.incrementRetry(targetClass)
                lastException = e

                if (attempt == MAX_RETRIES) {
                    retryMetrics.incrementExhausted(targetClass)
                    throw RuntimeException(
                        "Transient transaction error persisted after $MAX_RETRIES attempts for " +
                            "$targetClass.${joinPoint.signature.name}",
                        e,
                    )
                }

                logger.warn(
                    "Transient transaction error on attempt {}/{} for {}.{}, retrying...",
                    attempt,
                    MAX_RETRIES,
                    targetClass,
                    joinPoint.signature.name,
                )
                // Thread.sleep is intentional: AOP around-advice cannot be a suspend
                // function, and the intercepted @Transactional methods are synchronous.
                try {
                    Thread.sleep(BACKOFF_MS * attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw RuntimeException(
                        "Thread interrupted during Mongo transient transaction retry backoff",
                        ie,
                    )
                }
            }
        }
        // Unreachable: loop always returns or throws. Kept for compiler satisfaction.
        throw lastException!!
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val BACKOFF_MS = 100L

        fun isTransientTransactionError(e: Throwable): Boolean {
            var cause: Throwable? = e
            while (cause != null) {
                if (
                    cause is MongoException &&
                        cause.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                ) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }
    }
}
