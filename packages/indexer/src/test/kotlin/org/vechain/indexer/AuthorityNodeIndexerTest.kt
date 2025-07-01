package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.FileFixtures.abiFiles
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_AUTHORITY_NODE
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodeService
import org.vechain.indexer.thor.client.DefaultThorClient

@ExtendWith(MockKExtension::class)
class AuthorityNodeIndexerTest {
    @MockK private lateinit var authorityNodeRepository: AuthorityNodeRepository

    @MockK private lateinit var authorityNodeService: AuthorityNodeService
    private lateinit var authorityNodeIndexer: AuthorityNodeIndexer

    private val AUTHORITY_CONTRACT = "0x0000000000000000000000417574686f72697479"
    private val ACTION_ADDED = "0x6164646564000000000000000000000000000000000000000000000000000000"
    private val ACTION_REVOKED =
        "0x7265766f6b656400000000000000000000000000000000000000000000000000"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiManager = AbiManager(abiFiles)

        authorityNodeIndexer =
            AuthorityNodeIndexer(
                authorityNodeRepository = authorityNodeRepository,
                authorityNodeService = authorityNodeService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                startBlock = 0L,
                syncLogInterval = 1000L,
                contractAddress = AUTHORITY_CONTRACT,
                syncBlockBatchSize = 1000L,
            )
    }

    @Test
    fun `should process candidate added event`() {
        every { authorityNodeRepository.save(any()) } returns mockk()

        // Create logs with ADDED action
        val addedLogs = LOGS_AUTHORITY_NODE.map { log -> log.copy(data = ACTION_ADDED) }
        authorityNodeIndexer.processLogs(addedLogs, emptyList())

        verify {
            authorityNodeRepository.save(
                match { node -> node.nodeMaster == "0xdbe84597403b9aec770aef4a93a3065b3b58d306" }
            )
        }
    }

    @Test
    fun `should process candidate revoked event`() {
        every { authorityNodeRepository.deleteById(any()) } returns Unit

        // Create logs with REVOKED action
        val revokedLogs = LOGS_AUTHORITY_NODE.map { log -> log.copy(data = ACTION_REVOKED) }
        authorityNodeIndexer.processLogs(revokedLogs, emptyList())

        verify { authorityNodeRepository.deleteById("0xdbe84597403b9aec770aef4a93a3065b3b58d306") }
    }

    @Test
    fun `process logs triggers full sync`() {
        // save first then check if synced
        every { authorityNodeRepository.save(any()) } returns mockk()
        every { authorityNodeService.syncEndorsersForAllNodes() } returns Unit
        authorityNodeIndexer.status = Status.FULLY_SYNCED

        authorityNodeIndexer.processLogs(LOGS_AUTHORITY_NODE, emptyList())
        // Verify endorsers were synced
        verify { authorityNodeService.syncEndorsersForAllNodes() }
    }
}
