package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_2395_FUNGIBLE_TRANSFERS_1
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_2396_FUNGIBLE_TRANSFERS_2
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedFungibleTokenContracts
import org.vechain.indexer.repository.ArchiveRepository
import org.vechain.indexer.repository.FungibleTokenContractsRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.utils.IdUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@ExtendWith(MockKExtension::class)
class FungibleTokenContractIndexerTest {

    private lateinit var fungibleTokenContractIndexer: FungibleTokenContractIndexer
    private lateinit var archiveService: ArchiveService

    @MockK lateinit var fungibleTokenContractRepository: FungibleTokenContractsRepository

    @MockK lateinit var archiveRepository: ArchiveRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        archiveService = ArchiveService(archiveRepository, mongoTemplate)
        fungibleTokenContractIndexer =
          FungibleTokenContractIndexer(
            fungibleTokenContractRepository,
            archiveService,
            "http://localhost:8669",
            0L
          )
    }

    @Test
    fun `can process first time entries`() {
        every { fungibleTokenContractRepository.findByIdOrNull(any()) } returns null

        val fungibleContractsSlot = slot<List<IndexedFungibleTokenContracts>>()
        every { fungibleTokenContractRepository.saveAll(capture(fungibleContractsSlot)) } returns
          mockk()

        fungibleTokenContractIndexer.processBlock(BLOCK_2395_FUNGIBLE_TRANSFERS_1)

        verify { archiveRepository.save(any()) wasNot Called }
        expectThat(fungibleContractsSlot.captured.size).isEqualTo(10)
    }

    @Test
    fun `can process subsequent entries`() {
        val existingContracts =
          IndexedFungibleTokenContracts(
            tokenOwner = "0x361277d1b27504f36a3b33d3a52d1f8270331b8c",
            tokenAddresses = sortedSetOf("0xe9d3f9be0bb0dcd2f3dbe863fc93762319381455"),
            version = 1,
            blockNumber = 2395,
            blockId = "0x0000095b575427e8c57e3f43486763f35af8df29fd4087906288b139473b03df",
            blockTimestamp = 1687853184
          )

        val fungibleContractsSlot = slot<List<IndexedFungibleTokenContracts>>()
        val archiveSlot = slot<List<Archive<IndexedFungibleTokenContracts>>>()

        every { fungibleTokenContractRepository.findByIdOrNull(any()) } returns null
        every {
            fungibleTokenContractRepository.findByIdOrNull(existingContracts.tokenOwner)
        } returns existingContracts
        every { fungibleTokenContractRepository.saveAll(capture(fungibleContractsSlot)) } returns
          mockk()
        every { archiveRepository.saveAll(capture(archiveSlot)) } returns mockk()

        fungibleTokenContractIndexer.processBlock(BLOCK_2396_FUNGIBLE_TRANSFERS_2)

        expect {
            that(archiveSlot.captured.size).isEqualTo(1)
            that(archiveSlot.captured[0].data).isEqualTo(existingContracts)
        }

        val updatedContracts =
          fungibleContractsSlot.captured.find { it.tokenOwner == existingContracts.tokenOwner }

        expect {
            that(updatedContracts).isNotNull()
            that(updatedContracts?.version).isEqualTo(2)
        }
    }

    @Test
    fun `subsequent entries with existing contracts don't get updated`() {
        val existingContracts =
          IndexedFungibleTokenContracts(
            tokenOwner = "0x361277d1b27504f36a3b33d3a52d1f8270331b8c",
            tokenAddresses =
              sortedSetOf(
                "0x057a362324641972bd7d4cb61427abfc0184715c",
                "0xe9d3f9be0bb0dcd2f3dbe863fc93762319381455"
              ),
            version = 1,
            blockNumber = 2395,
            blockId = "0x0000095b575427e8c57e3f43486763f35af8df29fd4087906288b139473b03df",
            blockTimestamp = 1687853184
          )

        val fungibleContractsSlot = slot<List<IndexedFungibleTokenContracts>>()

        every { fungibleTokenContractRepository.findByIdOrNull(any()) } returns null
        every {
            fungibleTokenContractRepository.findByIdOrNull(existingContracts.tokenOwner)
        } returns existingContracts
        every { fungibleTokenContractRepository.saveAll(capture(fungibleContractsSlot)) } returns
          mockk()
        every { archiveRepository.saveAll(any<List<Archive<*>>>()) } returns mockk()

        fungibleTokenContractIndexer.processBlock(BLOCK_2396_FUNGIBLE_TRANSFERS_2)

        verify { archiveRepository.saveAll(any<List<Archive<*>>>()) wasNot Called }

        // Usually its 10, but we expect 1 less
        expectThat(fungibleContractsSlot.captured.size).isEqualTo(9)
    }

    @Test
    fun `can rollback to previous state`() {
        val previousState =
          IndexedFungibleTokenContracts(
            tokenOwner = "0x361277d1b27504f36a3b33d3a52d1f8270331b8c",
            tokenAddresses = sortedSetOf("0xe9d3f9be0bb0dcd2f3dbe863fc93762319381455"),
            version = 1,
            blockNumber = 2395,
            blockId = "0x0000095b575427e8c57e3f43486763f35af8df29fd4087906288b139473b03df",
            blockTimestamp = 1687853184
          )

        val currentState =
          IndexedFungibleTokenContracts(
            tokenOwner = "0x361277d1b27504f36a3b33d3a52d1f8270331b8c",
            tokenAddresses =
              sortedSetOf(
                "0x057a362324641972bd7d4cb61427abfc0184715c",
                "0xe9d3f9be0bb0dcd2f3dbe863fc93762319381455"
              ),
            version = 2,
            blockNumber = 2396,
            blockId = "0x0000095b575427e8c57e3f43486763f35af8df29fd4087906288b139473b03df",
            blockTimestamp = 1687853184
          )

        val archive =
          Archive(
            id = IdUtils.buildArchiveId(previousState),
            data = previousState,
          )

        val fungibleContractsSlot = mutableListOf<IndexedFungibleTokenContracts>()
        val archiveDeleteSlot = slot<List<String>>()

        val bulkOps: BulkOperations = mockk(relaxed = true)

        every { mongoTemplate.bulkOps(any(), IndexedFungibleTokenContracts::class.java) } returns
          bulkOps
        every { bulkOps.replaceOne(any(), capture(fungibleContractsSlot)) } returns bulkOps

        every { mongoTemplate.find<IndexedFungibleTokenContracts>(any(), any()) } returns
          mutableListOf(currentState)
        every { archiveRepository.findAllById(listOf(archive.id)) } returns listOf(archive)
        every { archiveRepository.deleteAllById(capture(archiveDeleteSlot)) } returns Unit

        fungibleTokenContractIndexer.rollback(currentState.blockNumber)

        expect {
            that(archiveDeleteSlot.captured.first()).isEqualTo(archive.id)
            that(fungibleContractsSlot.first()).isEqualTo(previousState)
        }
    }
}
