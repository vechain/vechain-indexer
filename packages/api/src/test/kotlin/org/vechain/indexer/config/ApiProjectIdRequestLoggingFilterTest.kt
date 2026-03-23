package org.vechain.indexer.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEmpty

class ApiProjectIdRequestLoggingFilterTest {

    private val filter = ApiProjectIdRequestLoggingFilter()
    private val logger =
        LoggerFactory.getLogger(ApiProjectIdRequestLoggingFilter::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `logs project id and templated endpoint when header is present`() {
        val request = MockHttpServletRequest("GET", "/api/v1/accounts/0x123")
        val response = MockHttpServletResponse()
        request.addHeader("X-Project-Id", "veworld-web")
        request.setAttribute(
            HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
            "/api/v1/accounts/{address}",
        )

        filter.doFilter(request, response, noopFilterChain())

        expectThat(appender.list).hasSize(1)
        expectThat(appender.list.single().formattedMessage).contains("event=api_request")
        expectThat(appender.list.single().formattedMessage).contains("project_id=\"veworld-web\"")
        expectThat(appender.list.single().formattedMessage)
            .contains("endpoint=\"/api/v1/accounts/{address}\"")
        expectThat(appender.list.single().formattedMessage).contains("method=GET")
    }

    @Test
    fun `logs unknown project id when header is missing`() {
        val request = MockHttpServletRequest("GET", "/api/v1/validators")
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/validators")

        filter.doFilter(request, response, noopFilterChain())

        expectThat(appender.list).hasSize(1)
        expectThat(appender.list.single().formattedMessage).contains("project_id=\"unknown\"")
    }

    @Test
    fun `logs unmatched endpoint when no route template is available`() {
        val request = MockHttpServletRequest("GET", "/not-found")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, noopFilterChain())

        expectThat(appender.list).hasSize(1)
        expectThat(appender.list.single().formattedMessage).contains("endpoint=\"unmatched\"")
    }

    @Test
    fun `does not log actuator requests`() {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, noopFilterChain())

        expectThat(appender.list).isEmpty()
    }

    private fun noopFilterChain(): FilterChain = FilterChain { _, _ -> }
}
