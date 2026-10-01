package org.vechain.indexer.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsUtils
import org.springframework.web.filter.OncePerRequestFilter

/** Answers a caller without `Origin` with `*` too: the edge serves its cached copy to browsers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AnyOriginFilter : OncePerRequestFilter() {

    // Spring's CORS handling owns these and skips a response that already carries the header.
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        CorsUtils.isCorsRequest(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        filterChain.doFilter(request, response)
    }
}
