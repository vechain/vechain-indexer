package org.vechain.indexer.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ProjectIdRequestFilterTest {

    private val registry = SimpleMeterRegistry()
    private val filter =
        ProjectIdRequestFilter(
            registry,
            ProjectIdsProperties(whitelist = listOf("stargate", "veworld")),
        )

    @Test
    fun `records whitelisted project id`() {
        invoke(header = "stargate")
        expectThat(count("stargate")).isEqualTo(1.0)
    }

    @Test
    fun `records unknown when header is missing`() {
        invoke(header = null)
        expectThat(count("unknown")).isEqualTo(1.0)
    }

    @Test
    fun `records unknown when header is blank`() {
        invoke(header = "   ")
        expectThat(count("unknown")).isEqualTo(1.0)
    }

    @Test
    fun `records other for value not in whitelist`() {
        invoke(header = "some-unrecognised")
        expectThat(count("other")).isEqualTo(1.0)
    }

    @Test
    fun `matches case-insensitively and normalises tag value to lowercase`() {
        invoke(header = "Stargate")
        expectThat(count("stargate")).isEqualTo(1.0)
    }

    @Test
    fun `skips actuator paths without recording`() {
        invoke(header = "stargate", uri = "/actuator/health")
        expectThat(count("stargate")).isEqualTo(0.0)
        expectThat(count("unknown")).isEqualTo(0.0)
    }

    @Test
    fun `increments cumulatively across requests`() {
        invoke(header = "veworld")
        invoke(header = "veworld")
        invoke(header = "veworld")
        expectThat(count("veworld")).isEqualTo(3.0)
    }

    @Test
    fun `trims whitespace and drops empty entries in the whitelist`() {
        val registry = SimpleMeterRegistry()
        val filter =
            ProjectIdRequestFilter(
                registry,
                ProjectIdsProperties(whitelist = listOf(" stargate", "veworld ", "", "  ")),
            )
        val req = MockHttpServletRequest("GET", "/accounts/0xabc")
        req.addHeader("X-Project-Id", "veworld")
        filter.doFilter(req, MockHttpServletResponse(), MockFilterChain())
        expectThat(registry.counter("api_requests_by_project_total", "project", "veworld").count())
            .isEqualTo(1.0)
    }

    private fun invoke(header: String?, uri: String = "/accounts/0xabc") {
        val req = MockHttpServletRequest("GET", uri)
        if (header != null) req.addHeader("X-Project-Id", header)
        filter.doFilter(req, MockHttpServletResponse(), MockFilterChain())
    }

    private fun count(project: String): Double =
        registry.counter("api_requests_by_project_total", "project", project).count()
}
