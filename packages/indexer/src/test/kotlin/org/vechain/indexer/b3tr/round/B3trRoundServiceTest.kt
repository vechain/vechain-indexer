package org.vechain.indexer.b3tr.round

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult

class B3trRoundServiceTest {
    private val thorClient: ThorClient = mockk()
    private val blockId = "0x" + "a".repeat(64)

    private lateinit var service: B3trRoundService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            B3trRoundService(
                thorClient = thorClient,
                emissionsContractAddress = "0x0000000000000000000000000000000000000def",
            )
    }

    @Test
    fun `getCurrentRound decodes current cycle at provided revision`() {
        coEvery { thorClient.inspectClauses(any(), BlockRevision.Id(blockId)) } returns
            listOf(inspectionResult(HexUtils.toHex(7L, 64)))

        val currentRound = runBlocking { service.getCurrentRound(blockId) }

        assertEquals(7, currentRound)
    }

    @Test
    fun `parseCurrentRound returns null for reverted response`() {
        val currentRound =
            service.parseCurrentRound(
                InspectionResult(
                    data = "0x",
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = true,
                    vmError = "execution reverted",
                )
            )

        assertNull(currentRound)
    }

    private fun inspectionResult(data: String) =
        InspectionResult(
            data = data,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
            reverted = false,
            vmError = null,
        )
}
