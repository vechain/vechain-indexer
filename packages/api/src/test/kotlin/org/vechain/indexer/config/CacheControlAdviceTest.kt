package org.vechain.indexer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

internal class CacheControlAdviceTest {

    private val advice = CacheControlAdvice()
    private val response = MockHttpServletResponse()

    @Suppress("unused")
    private class Handlers {
        @CacheFor(CachePolicy.HOURLY) fun declared(): String = "body"

        fun undeclared(): String = "body"
    }

    private fun parameterFor(method: String) =
        MethodParameter(Handlers::class.java.getDeclaredMethod(method), -1)

    private fun write(method: String, granted: String? = null): String? {
        val outputMessage = ServletServerHttpResponse(response)
        // How a ResponseEntity's own headers reach the advice: copied in, not yet flushed.
        granted?.let { outputMessage.headers.set(HttpHeaders.CACHE_CONTROL, it) }
        advice.beforeBodyWrite(
            "body",
            parameterFor(method),
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter::class.java,
            ServletServerHttpRequest(MockHttpServletRequest()),
            outputMessage,
        )
        // The converter writing the body is what flushes headers onto the servlet response.
        outputMessage.flush()
        return response.getHeader(HttpHeaders.CACHE_CONTROL)
    }

    @Test
    fun `a declared endpoint gets the window it asked for`() {
        assertEquals(CachePolicy.HOURLY.headerValue, write("declared"))
    }

    @Test
    fun `a window the handler already granted itself is left alone`() {
        // cachedByAge and cachedFor reach the advice this way, and must not be overwritten.
        assertEquals("public, max-age=999", write("declared", granted = "public, max-age=999"))
    }

    @Test
    fun `an error never inherits the endpoint's window`() {
        response.status = 404

        assertEquals(CachePolicy.VOLATILE.headerValue, write("declared"))
    }

    @Test
    fun `an undeclared return type is not this advice's business`() {
        // Exception handlers and springdoc reach the converters too, and must pass through.
        assertFalse(
            advice.supports(
                parameterFor("undeclared"),
                MappingJackson2HttpMessageConverter::class.java,
            )
        )
        assertTrue(
            advice.supports(
                parameterFor("declared"),
                MappingJackson2HttpMessageConverter::class.java,
            )
        )
    }
}
