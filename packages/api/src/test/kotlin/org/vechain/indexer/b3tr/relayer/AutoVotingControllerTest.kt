package org.vechain.indexer.b3tr.relayer

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class AutoVotingControllerTest {

    @MockK lateinit var service: AutoVotingService

    private lateinit var controller: AutoVotingController

    @BeforeEach
    fun setUp() {
        controller = AutoVotingController(service)
    }

    @Test
    fun `getEnabledAtRound forwards roundId to service and returns full set`() {
        every { service.findEnabledAddressesAtRound(roundId = 12) } returns listOf("0xa", "0xb")

        val response = controller.getEnabledAtRound(roundId = 12)

        assertEquals(listOf("0xa", "0xb"), response)
        verify(exactly = 1) { service.findEnabledAddressesAtRound(12) }
    }
}
