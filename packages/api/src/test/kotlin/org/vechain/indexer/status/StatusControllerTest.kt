package org.vechain.indexer.status

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.postgres.IndexerCheckpoint
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

@ExtendWith(MockKExtension::class)
internal class StatusControllerTest {

    @MockK lateinit var statusService: StatusService

    @Test
    fun `every indexer is reported, including one that has written nothing`() {
        val checkpoints =
            listOf(
                IndexerCheckpoint("blocks", 3, 25_909_780, "0x018b5a14"),
                IndexerCheckpoint("history", 1, null, null),
            )
        every { statusService.checkpoints() } returns checkpoints

        assertEquals(checkpoints, StatusController(statusService).getStatus())
    }

    @Test
    fun `the head moves every block, so the response is never reused`() {
        val annotation =
            StatusController::class.java.getMethod("getStatus").getAnnotation(CacheFor::class.java)

        assertEquals(CachePolicy.VOLATILE, annotation.policy)
    }
}
