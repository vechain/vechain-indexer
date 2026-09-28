package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.ActionPeriod.Round
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.thor.Address

internal class ActionControllerTest {
    private val service = mockk<ActionService>()
    private val controller = ActionController(service)

    private val wallet = Address("0x" + "a".repeat(40))
    private val appId = AppId("0x" + "7".repeat(64))

    init {
        every { service.overviewPolicy(Round(CLOSED), any()) } returns CachePolicy.IMMUTABLE
        every { service.overviewPolicy(Round(OPEN), any()) } answers { secondArg() }
        every { service.getUserOverview(any(), any()) } returns mockk()
        every { service.getUserAppOverview(any(), any(), any()) } returns mockk()
        every { service.getAppOverview(any(), any()) } returns mockk()
        every { service.getGlobalOverview(any()) } returns mockk()
    }

    private fun assertCachedFor(open: CachePolicy, overview: (Int) -> ResponseEntity<*>) {
        assertEquals(CachePolicy.IMMUTABLE.headerValue, cacheControl(overview(CLOSED)))
        assertEquals(open.headerValue, cacheControl(overview(OPEN)))
    }

    private fun cacheControl(response: ResponseEntity<*>) =
        response.headers.getFirst(HttpHeaders.CACHE_CONTROL)

    @Test
    fun `a user overview of a closed round is final, an open one is hourly`() =
        assertCachedFor(CachePolicy.HOURLY) { controller.getUserOverview(wallet, it, null) }

    @Test
    fun `a user's app overview of a closed round is final, an open one is volatile`() =
        assertCachedFor(CachePolicy.VOLATILE) {
            controller.getUserAppOverview(wallet, appId, it, null)
        }

    @Test
    fun `an app overview of a closed round is final, an open one is hourly`() =
        assertCachedFor(CachePolicy.HOURLY) { controller.getAppOverview(appId, it, null) }

    @Test
    fun `the global overview of a closed round is final, an open one is daily`() =
        assertCachedFor(CachePolicy.DAILY) { controller.getGlobalOverview(it, null) }

    companion object {
        private const val CLOSED = 112
        private const val OPEN = 113
    }
}
