package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.FileFixtures.abiFiles
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_AUTHORITY_NODE
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodeEventService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.ParamUtils.getAsString

@ExtendWith(MockKExtension::class)
class AuthorityNodeEndorserIndexerTest {
    @MockK private lateinit var authorityNodeRepository: AuthorityNodeRepository
    @MockK private lateinit var authorityNodeEventService: AuthorityNodeEventService
    private lateinit var authorityNodeEndorserIndexer: AuthorityNodeEndorserIndexer

    private val AUTHORITY_CONTRACT = "0x0000000000000000000000417574686f72697479"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiManager = AbiManager(abiFiles)

        authorityNodeEndorserIndexer =
            AuthorityNodeEndorserIndexer(
                authorityNodeRepository = authorityNodeRepository,
                authorityNodeEventService = authorityNodeEventService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                startBlock = 0L,
                syncLogInterval = 1000L,
                contractAddress = AUTHORITY_CONTRACT,
                syncBlockBatchSize = 1000L,
            )
    }

    @Test
    fun `should process candidate events when not fully synced`() {
        val eventsSlot = slot<List<IndexedEvent>>()
        every {
            authorityNodeEventService.processCandidateEvents(capture(eventsSlot), false)
        } returns Unit

        authorityNodeEndorserIndexer.processLogs(LOGS_AUTHORITY_NODE, emptyList())

        verify { authorityNodeEventService.processCandidateEvents(any(), false) }

        val capturedEvents = eventsSlot.captured
        assertThat(capturedEvents).isNotEmpty()

        capturedEvents.first().let { event ->
            assertThat(event.params.getAsString("nodeMaster"))
                .isEqualTo("0xdbe84597403b9aec770aef4a93a3065b3b58d306")
            assertThat(event.params.getAsString("action"))
                .isEqualTo(AuthorityNodeEventService.ACTION_ADDED)
            assertThat(event.blockId).isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockID)
            assertThat(event.blockNumber).isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockNumber)
            assertThat(event.blockTimestamp)
                .isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockTimestamp)
        }
    }

    @Test
    fun `should process candidate revoked events when not fully synced`() {
        val eventsSlot = slot<List<IndexedEvent>>()
        every {
            authorityNodeEventService.processCandidateEvents(capture(eventsSlot), false)
        } returns Unit

        // Create logs with REVOKED action
        val revokedLogs =
            LOGS_AUTHORITY_NODE.map { log ->
                log.copy(data = AuthorityNodeEventService.ACTION_REVOKED)
            }

        authorityNodeEndorserIndexer.processLogs(revokedLogs, emptyList())

        verify { authorityNodeEventService.processCandidateEvents(any(), false) }

        val capturedEvents = eventsSlot.captured
        assertThat(capturedEvents).isNotEmpty()

        capturedEvents.first().let { event ->
            assertThat(event.params.getAsString("nodeMaster"))
                .isEqualTo("0xdbe84597403b9aec770aef4a93a3065b3b58d306")
            assertThat(event.params.getAsString("action"))
                .isEqualTo(AuthorityNodeEventService.ACTION_REVOKED)
            assertThat(event.blockId).isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockID)
            assertThat(event.blockNumber).isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockNumber)
            assertThat(event.blockTimestamp)
                .isEqualTo(LOGS_AUTHORITY_NODE.first().meta.blockTimestamp)
        }
    }

    @Test
    fun `should only process candidate events when fully synced`() {
        authorityNodeEndorserIndexer.status = Status.FULLY_SYNCED

        val eventsSlot = slot<List<IndexedEvent>>()
        every {
            authorityNodeEventService.processCandidateEvents(capture(eventsSlot), true)
        } returns Unit
        every { authorityNodeEventService.syncEndorsersForAllNodes() } returns Unit

        authorityNodeEndorserIndexer.processLogs(LOGS_AUTHORITY_NODE, emptyList())

        verify { authorityNodeEventService.processCandidateEvents(any(), true) }
        verify(exactly = 0) { authorityNodeEventService.syncEndorsersForAllNodes() }
    }
}
