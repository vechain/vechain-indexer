package org.vechain.indexer.b3tr.navigator

import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
}
