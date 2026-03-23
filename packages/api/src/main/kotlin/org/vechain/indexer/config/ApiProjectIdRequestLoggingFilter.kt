package org.vechain.indexer.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

@Component
class ApiProjectIdRequestLoggingFilter : OncePerRequestFilter() {

    companion object {
        private const val PROJECT_ID_HEADER = "X-Project-Id"
        private const val UNKNOWN_PROJECT_ID = "unknown"
        private const val UNMATCHED_ENDPOINT = "unmatched"
        private const val ACTUATOR_PATH_PREFIX = "/actuator"
    }

    private val requestLogger = LoggerFactory.getLogger(this::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.requestURI == ACTUATOR_PATH_PREFIX ||
            request.requestURI.startsWith("$ACTUATOR_PATH_PREFIX/")
    }

    override fun shouldNotFilterAsyncDispatch(): Boolean = true

    override fun shouldNotFilterErrorDispatch(): Boolean = true

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val projectId = normalizeProjectId(request.getHeader(PROJECT_ID_HEADER))

        filterChain.doFilter(request, response)

        requestLogger.info(
            "event=api_request project_id=\"{}\" endpoint=\"{}\" method={}",
            sanitizeForLog(projectId),
            sanitizeForLog(resolveEndpoint(request)),
            request.method,
        )
    }

    private fun normalizeProjectId(projectId: String?): String {
        val trimmed = projectId?.trim().orEmpty()
        return if (trimmed.isBlank()) UNKNOWN_PROJECT_ID else trimmed
    }

    private fun resolveEndpoint(request: HttpServletRequest): String {
        val bestMatch = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
        return bestMatch?.toString()?.ifBlank { UNMATCHED_ENDPOINT } ?: UNMATCHED_ENDPOINT
    }

    private fun sanitizeForLog(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", " ")
    }
}
