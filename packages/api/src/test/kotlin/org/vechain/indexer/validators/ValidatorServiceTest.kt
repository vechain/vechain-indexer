package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

class ValidatorServiceTest {
    private val validatorBlockRepository: ValidatorBlockRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val thorClient: ThorClient = mockk()

    private val service =
        ValidatorService(
            validatorBlockRepository = validatorBlockRepository,
            mongoTemplate = mongoTemplate,
            thorClient = thorClient,
        )

    // -- getValidatorBlockRewards tests --

    @Test
    fun `getValidatorBlockRewards returns all records when no filters provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val block1 = validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED)
        val block2 = validatorBlock(blockNumber = 99, status = BlockStatus.MISSED)

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1, block2)

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).containsExactly(block1, block2)
    }

    @Test
    fun `getValidatorBlockRewards filters by status when provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val block1 = validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED)

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1)

        val result = service.getValidatorBlockRewards(null, BlockStatus.VALIDATED, pageable)

        expectThat(result.data).hasSize(1)
        expectThat(result.data.first().status).isEqualTo(BlockStatus.VALIDATED)
    }

    @Test
    fun `getValidatorBlockRewards filters by validator when provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val validator = Address("0x1234567890abcdef1234567890abcdef12345678")
        val block1 =
            validatorBlock(
                blockNumber = 100,
                status = BlockStatus.VALIDATED,
                validator = validator.value.lowercase(),
            )

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1)

        val result = service.getValidatorBlockRewards(validator, null, pageable)

        expectThat(result.data).hasSize(1)
        expectThat(result.data.first().validator).isEqualTo(validator.value.lowercase())
    }

    @Test
    fun `getValidatorBlockRewards respects ASC direction`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "blockNumber"))
        val block1 = validatorBlock(blockNumber = 99, status = BlockStatus.VALIDATED)
        val block2 = validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED)

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1, block2)

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).containsExactly(block1, block2)
    }

    @Test
    fun `getValidatorBlockRewards respects DESC direction`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val block1 = validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED)
        val block2 = validatorBlock(blockNumber = 99, status = BlockStatus.VALIDATED)

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1, block2)

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).containsExactly(block1, block2)
    }

    // -- hasNext pagination tests --

    @Test
    fun `hasNext is false when results equal pageSize (exact last page)`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val blocks =
            (1..3).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).hasSize(3)
        expectThat(result.pagination.hasNext).isFalse()
    }

    @Test
    fun `hasNext is true when more results exist beyond pageSize`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "blockNumber"))
        // Service fetches pageSize+1 = 4 items; DB returns 4 meaning there's a next page
        val blocks =
            (1..4).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).hasSize(3)
        expectThat(result.pagination.hasNext).isTrue()
    }

    @Test
    fun `hasNext is false when fewer results than pageSize`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val blocks =
            (1..2).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).hasSize(2)
        expectThat(result.pagination.hasNext).isFalse()
    }

    // -- getBlockByNumber tests --

    @Test
    fun `getBlockByNumber returns records for specific block`() {
        val block1 =
            validatorBlock(blockNumber = 12345, status = BlockStatus.VALIDATED, validator = "0xaaa")
        val block2 =
            validatorBlock(blockNumber = 12345, status = BlockStatus.MISSED, validator = "0xbbb")

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1, block2)

        val result = service.getBlockByNumber(12345, null)

        expectThat(result).hasSize(2)
    }

    @Test
    fun `getBlockByNumber with validator filter narrows to single record`() {
        val validator = Address("0x1234567890abcdef1234567890abcdef12345678")
        val block1 =
            validatorBlock(
                blockNumber = 12345,
                status = BlockStatus.VALIDATED,
                validator = validator.value.lowercase(),
            )

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns
            listOf(block1)

        val result = service.getBlockByNumber(12345, validator)

        expectThat(result).hasSize(1)
        expectThat(result.first().validator).isEqualTo(validator.value.lowercase())
    }

    @Test
    fun `getBlockByNumber returns empty list when no records found`() {
        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns emptyList()

        val result = service.getBlockByNumber(99999, null)

        expectThat(result).isEmpty()
    }

    // -- getValidatorById tests --

    @Test
    fun `getValidatorById returns validator when found`() {
        val validator = validator(id = "0x1234567890abcdef1234567890abcdef12345678")

        every { mongoTemplate.findOne(any<Query>(), Validator::class.java) } returns validator

        val result = service.getValidatorById("0x1234567890abcdef1234567890abcdef12345678")

        expectThat(result).isNotNull().and {
            get { id }.isEqualTo("0x1234567890abcdef1234567890abcdef12345678")
        }
    }

    @Test
    fun `getValidatorById returns null when not found`() {
        every { mongoTemplate.findOne(any<Query>(), Validator::class.java) } returns null

        val result = service.getValidatorById("0x0000000000000000000000000000000000000000")

        expectThat(result).isNull()
    }

    private fun validator(id: String): Validator =
        Validator(id = id, blockId = "0xblock1", blockNumber = 1, blockTimestamp = 10)

    private fun validatorBlock(
        blockNumber: Long,
        status: BlockStatus,
        validator: String = "0xdefault",
    ): ValidatorBlock =
        ValidatorBlock(
            id = "id-$blockNumber-$validator",
            blockId = "0xblock$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            validator = validator,
            blockReward = BigInteger("1000"),
            priorityReward = BigInteger("100"),
            total = BigInteger("1100"),
            status = status,
        )
}
