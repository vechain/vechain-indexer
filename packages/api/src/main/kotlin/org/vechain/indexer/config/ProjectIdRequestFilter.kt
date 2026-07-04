package org.vechain.indexer.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ProjectIdRequestFilter(
    private val registry: MeterRegistry,
    properties: ProjectIdsProperties,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val whitelist: Set<String> = properties.whitelist.map { it.lowercase() }.toSet()
    private val counters = ConcurrentHashMap<String, Counter>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            counterFor(classify(request.getHeader(HEADER_NAME))).increment()
        } catch (e: Exception) {
            log.warn("Failed to record project-id metric", e)
        }
        filterChain.doFilter(request, response)
    }

    private fun classify(raw: String?): String {
        val normalized = raw?.trim()?.lowercase().orEmpty()
        return when {
            normalized.isEmpty() -> UNKNOWN
            whitelist.contains(normalized) -> normalized
            else -> {
                log.info("Unrecognised {} header value: {}", HEADER_NAME, raw)
                OTHER
            }
        }
    }

    private fun counterFor(project: String): Counter =
        counters.computeIfAbsent(project) {
            Counter.builder(METRIC_NAME)
                .description("Count of API requests broken down by X-Project-Id header")
                .tag("project", project)
                .register(registry)
        }

    companion object {
        private const val HEADER_NAME = "X-Project-Id"
        private const val METRIC_NAME = "api_requests_by_project_total"
        private const val UNKNOWN = "unknown"
        private const val OTHER = "other"
    }
}
