package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.vechain.indexer.exception.BadRequestException

@ExtendWith(MockKExtension::class)
internal class NavigatorControllerTest {
    @MockK(relaxed = true) lateinit var navigatorApiService: NavigatorApiService

    private lateinit var controller: NavigatorController

    @BeforeEach
    fun setUp() {
        controller = NavigatorController(navigatorApiService)
    }

    @Test
    fun `getDelegations requires navigator or citizen filter`() {
        val exception =
            assertThrows(BadRequestException::class.java) {
                controller.getDelegations(null, null, null, 10, "desc")
            }

        assertEquals("Either navigator or citizen must be provided", exception.message)
    }

    @Test
    fun `getFeeHistory sorts by numeric roundId`() {
        val pageableSlot = slot<Pageable>()
        every { navigatorApiService.findFeeHistory("0xnav1", capture(pageableSlot)) } returns
            SliceImpl(emptyList())

        controller.getFeeHistory("0xnav1", null, 10, "desc")

        assertEquals(
            Sort.Direction.DESC,
            pageableSlot.captured.sort.getOrderFor("roundId")?.direction,
        )
        assertNotNull(pageableSlot.captured.sort.getOrderFor("_id"))
    }
}
