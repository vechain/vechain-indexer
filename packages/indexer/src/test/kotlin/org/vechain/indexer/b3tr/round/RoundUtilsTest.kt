package org.vechain.indexer.b3tr.round

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.round.RoundUtils.discoverRoundId
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

class RoundUtilsTest {
    @Nested
    inner class DiscoverRoundIdTest {
        @Test
        fun `discoverRoundId if no event should use the currRoundId`() {
            val roundId = discoverRoundId(emptyList(), currRound = 11)

            assertEquals(11, roundId)
        }

        @Test
        fun `discoverRoundId should prioritise roundId in the event`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val roundChangeEvent =
                buildIndexedEvent(
                    id = "e1",
                    blockId = blockDetailsEvent1.blockId,
                    blockNumber = blockDetailsEvent1.blockNumber,
                    blockTimestamp = blockDetailsEvent1.blockTimestamp,
                    eventType = "EmissionDistributed",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "cycle" to "3",
                                    "totalAmount" to "10000000000000000000",
                                    "distributor" to "0x0",
                                )
                        ),
                )

            val roundId = discoverRoundId(listOf(roundChangeEvent), currRound = 5)

            assertEquals(3, roundId)
        }
    }
}
