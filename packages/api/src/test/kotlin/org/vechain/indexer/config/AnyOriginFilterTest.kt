package org.vechain.indexer.config

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

@SpringJUnitWebConfig(AnyOriginFilterTest.Context::class)
internal class AnyOriginFilterTest(context: WebApplicationContext) {

    @Configuration @EnableWebMvc @Import(WebMvcConfig::class, Probe::class) open class Context

    @RestController
    open class Probe {
        @CacheFor(CachePolicy.HOURLY) @GetMapping("/probe") fun probe(): String = "ok"
    }

    private val mvc: MockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilter<DefaultMockMvcBuilder>(AnyOriginFilter())
            .build()

    @Test
    fun `a request without Origin still gets the wildcard`() {
        mvc.perform(get("/probe"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
    }

    @Test
    fun `a same-origin Origin is not CORS to Spring, so the filter answers`() {
        mvc.perform(get("/probe").header(HttpHeaders.ORIGIN, "http://localhost"))
            .andExpect(status().isOk)
            .andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
    }

    @Test
    fun `a request with Origin gets one wildcard from Spring`() {
        mvc.perform(get("/probe").header(HttpHeaders.ORIGIN, "https://governance.vebetterdao.org"))
            .andExpect(status().isOk)
            .andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
    }

    @Test
    fun `a preflight still lists the allowed methods and headers`() {
        mvc.perform(
                options("/probe")
                    .header(HttpHeaders.ORIGIN, "https://governance.vebetterdao.org")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-project-id")
            )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,OPTIONS"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "x-project-id"))
    }
}
