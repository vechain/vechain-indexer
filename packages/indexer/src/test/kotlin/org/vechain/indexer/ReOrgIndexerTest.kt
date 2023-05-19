package org.vechain.indexer

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.assertions.isEqualTo

internal class ReOrgIndexerTest {

    private val thorService: ThorService = mockk()
    private val repo: BlockRepo = mockk()
    private val mongoTemplate: MongoTemplate = mockk()

    fun mockBlock(num: Long, parentId: String): Block {
        return Block(
            blockId = "0x${num}",
            blockNumber = num,
            blockTimestamp = num,
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
                repo.getMaxBlockId()
            } returns null andThen "0x37"

            //Mock the first response and then the response after the re-org
            every {
                repo.getMaxBlockNumber()
            } returns null andThen 37

            every {
                mongoTemplate.insertAll(any<List<Block>>())
            } returns emptyList<Block>()

            //Set up capture slots
            val startBlock = slot<Long>()
            val endBlock = slot<Long>()

            every {
                repo.deleteAllByBlockNumberBetween(capture(startBlock), capture(endBlock))
            } answers {
                println("Deleting blocks")
            }

            val indexer = BlockIndexer(thorService, repo, mongoTemplate)

            //Mock 50 blocks to process
            for (i in 0..51) {

                val mockedBlock = mockBlock(
                    num = i.toLong(),
                    parentId = "0x${i - 1}"
                )

                every { thorService.getBlock(i.toLong()) } returns mockedBlock
                every { repo.save(mockedBlock) } returns mockedBlock
            }

            //Mock a re-org block
            every { thorService.getBlock(51) } returns
                    mockBlock(51, "0x123412341234") andThen
                    mockBlock(51, "0x50")

            every { thorService.getBlock(52) } throws BlockNotFoundException("Block not found", 52)
            
            // Start the indexer
            Thread {
                indexer.start()
            }.start()

            // and wait for the blocks to process
            for (i in 0..120) {
                if (indexer.currentBlockNumber == 52L) {
                    break
                } else {
                    Thread.sleep(250)
                }
            }

            //Check that the re-org happened
            expect { that(startBlock.captured).isEqualTo(38) }
            expect { that(endBlock.captured).isEqualTo(52) }
        }
    }

}