package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class ReOrgIndexerTest {

    @MockK
    lateinit var thorService: ThorService

    @MockK
    lateinit var repo: BlockRepo

    @MockK
    lateinit var mongoTemplate: MongoTemplate

    lateinit var blockIndexer: BlockIndexer

    @BeforeEach
    fun setUp() {
        every { thorService.getBlock(0) } returns BlockFixtures.BLOCK_0_GENESIS
        MockKAnnotations.init(this)
        blockIndexer = BlockIndexer(thorService, repo)
    }

    fun mockBlock(num: Long, parentId: String): Block {
        return Block(
            id = "0x${num}",
            number = num,
            timestamp = num,
            size = 0,
            gasUsed = 0,
            gasLimit = 0,
            parentID = parentId,
            beneficiary = "",
            totalScore = 0,
            txsRoot = "",
            stateRoot = "",
            receiptsRoot = "",
            txsFeatures = 0,
            com = false,
            signer = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
            isTrunk = false,
            isFinalized = false,
            transactions = emptyList()
        )
    }

    @Nested
    inner class ReOrgTest {
        @Test
        fun `should resolve re-org`() {

            //Mock the first response and then the response after the re-org
            every {
                repo.getMaxBlockNumber()
            } returns null andThen 37

            every {
                mongoTemplate.insertAll(any<List<IndexedBlock>>())
            } returns emptyList<IndexedBlock>()

            //Set up capture slots
            val startBlock = slot<Long>()
            val endBlock = slot<Long>()

            every {
                repo.deleteAllByBlockNumberBetween(capture(startBlock), capture(endBlock))
            } answers {
                println("Deleting blocks")
            }

            //Mock 50 blocks to process
            for (i in 0..51) {

                val mockedBlock = mockBlock(
                    num = i.toLong(),
                    parentId = "0x${i - 1}"
                )

                every { thorService.getBlock(i.toLong()) } returns mockedBlock
                every { repo.save(IndexedBlock(mockedBlock)) } returns IndexedBlock(mockedBlock)
            }

            //Mock a re-org block
            every { thorService.getBlock(51) } returns
                    mockBlock(51, "0x123412341234") andThen
                    mockBlock(51, "0x50")

            every { thorService.getBlock(52) } throws BlockNotFoundException("Block not found")

            // Start the indexer
            Thread {
                blockIndexer.start()
            }.start()

            // and wait for the blocks to process
            for (i in 0..120) {
                if (blockIndexer.currentBlockNumber == 52L) {
                    break
                } else {
                    Thread.sleep(250)
                }
            }

            //Check that the re-org happened
            expect { that(startBlock.captured).isEqualTo(36) }
            expect { that(endBlock.captured).isEqualTo(38) }
        }
    }

}