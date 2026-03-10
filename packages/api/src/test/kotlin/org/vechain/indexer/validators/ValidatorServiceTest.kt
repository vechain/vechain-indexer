package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    @Test
    fun `getValidatorHistoricBlocks includes boundary records for sampled ranges`() {
        val validator = "0xvalidator"
        val startBoundary =
            validatorBlock(blockNumber = 90, status = BlockStatus.VALIDATED, validator = validator)
        val sampled =
            listOf(
                validatorBlock(
                    blockNumber = 360,
                    status = BlockStatus.VALIDATED,
                    validator = validator,
                )
            )
        val endBoundary =
            validatorBlock(blockNumber = 600, status = BlockStatus.VALIDATED, validator = validator)

        every {
            validatorBlockRepository.findHourlyInTimestampRange(1_000L, 6_000L, validator)
        } returns sampled
        every {
            validatorBlockRepository
                .findFirstByValidatorAndStatusAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    validator,
                    BlockStatus.VALIDATED,
                    1_000L,
                )
        } returns startBoundary
        every {
            validatorBlockRepository
                .findFirstByValidatorAndStatusAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    validator,
                    BlockStatus.VALIDATED,
                    6_000L,
                )
        } returns endBoundary

        val result = service.getValidatorHistoricBlocks(1_000L, 6_000L, validator)

        expectThat(result.map { it.blockTimestamp }).isEqualTo(listOf(900L, 3_600L, 6_000L))
    }

    @Test
    fun `getValidatorHistoricBlocks uses monthly samples for very large ranges`() {
        val validator = "0xvalidator"

        every {
            validatorBlockRepository.findMonthlyInTimestampRange(0L, 40_000_000L, validator)
        } returns emptyList()
        every {
            validatorBlockRepository
                .findFirstByValidatorAndStatusAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    validator,
                    BlockStatus.VALIDATED,
                    any(),
                )
        } returns null

        service.getValidatorHistoricBlocks(0L, 40_000_000L, validator)

        io.mockk.verify(exactly = 1) {
            validatorBlockRepository.findMonthlyInTimestampRange(0L, 40_000_000L, validator)
        }
    }

    // -- getValidatorBlockRewards tests --

    @Test
    fun `getValidatorBlockRewards builds empty query when no filters provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(
                validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED),
                validatorBlock(blockNumber = 99, status = BlockStatus.MISSED),
            )

        val result = service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(result.data).hasSize(2)
        expectThat(querySlot.captured.queryObject.keys).isEmpty()
        expectThat(querySlot.captured.sortObject["blockNumber"]).isEqualTo(-1)
    }

    @Test
    fun `getValidatorBlockRewards builds query with status criteria when status provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED))

        service.getValidatorBlockRewards(null, null, BlockStatus.VALIDATED, pageable)

        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(1)
        expectThat(andList[0]).isEqualTo(org.bson.Document("status", BlockStatus.VALIDATED))
    }

    @Test
    fun `getValidatorBlockRewards builds query with validator criteria when validator provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val validator = Address("0x1234567890abcdef1234567890abcdef12345678")
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(
                validatorBlock(
                    blockNumber = 100,
                    status = BlockStatus.VALIDATED,
                    validator = validator.value.lowercase(),
                )
            )

        service.getValidatorBlockRewards(validator, null, null, pageable)

        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(1)
        expectThat(andList[0])
            .isEqualTo(org.bson.Document("validator", validator.value.lowercase()))
    }

    @Test
    fun `getValidatorBlockRewards applies ASC sort from pageable`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(querySlot.captured.sortObject).isEqualTo(org.bson.Document("blockNumber", 1))
    }

    @Test
    fun `getValidatorBlockRewards applies DESC sort from pageable`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(querySlot.captured.sortObject).isEqualTo(org.bson.Document("blockNumber", -1))
    }

    // -- getValidatorBlockRewards blockNumber filter tests --

    @Test
    fun `getValidatorBlockRewards uses lte for blockNumber when sort is DESC`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, 500L, null, pageable)

        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(1)
        expectThat(andList[0])
            .isEqualTo(org.bson.Document("blockNumber", org.bson.Document("\$lte", 500L)))
    }

    @Test
    fun `getValidatorBlockRewards uses gte for blockNumber when sort is ASC`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, 500L, null, pageable)

        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(1)
        expectThat(andList[0])
            .isEqualTo(org.bson.Document("blockNumber", org.bson.Document("\$gte", 500L)))
    }

    @Test
    fun `getValidatorBlockRewards combines validator, blockNumber, and status filters`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val validator = Address("0x1234567890abcdef1234567890abcdef12345678")
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(validator, 500L, BlockStatus.VALIDATED, pageable)

        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(3)
        expectThat(andList[0])
            .isEqualTo(org.bson.Document("validator", validator.value.lowercase()))
        expectThat(andList[1])
            .isEqualTo(org.bson.Document("blockNumber", org.bson.Document("\$lte", 500L)))
        expectThat(andList[2]).isEqualTo(org.bson.Document("status", BlockStatus.VALIDATED))
    }

    // -- hasNext pagination tests --

    @Test
    fun `hasNext is false when results equal pageSize (exact last page)`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val blocks =
            (1..3).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(result.data).hasSize(3)
        expectThat(result.pagination.hasNext).isFalse()
    }

    @Test
    fun `hasNext is true when more results exist beyond pageSize`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val blocks =
            (1..4).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(result.data).hasSize(3)
        expectThat(result.pagination.hasNext).isTrue()
    }

    @Test
    fun `hasNext is false when fewer results than pageSize`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val blocks =
            (1..2).map { validatorBlock(blockNumber = it.toLong(), status = BlockStatus.VALIDATED) }

        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns blocks

        val result = service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(result.data).hasSize(2)
        expectThat(result.pagination.hasNext).isFalse()
    }

    // -- getBlockByNumber tests --

    @Test
    fun `getBlockByNumber builds query with blockNumber criteria`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(
                validatorBlock(
                    blockNumber = 12345,
                    status = BlockStatus.VALIDATED,
                    validator = "0xaaa",
                ),
                validatorBlock(
                    blockNumber = 12345,
                    status = BlockStatus.MISSED,
                    validator = "0xbbb",
                ),
            )

        val result = service.getBlockByNumber(12345, null)

        expectThat(result).hasSize(2)
        val criteria = querySlot.captured.queryObject
        expectThat(criteria.get("\$and") as List<*>).hasSize(1)
        expectThat((criteria.get("\$and") as List<*>).first())
            .isEqualTo(org.bson.Document("blockNumber", 12345L))
    }

    @Test
    fun `getBlockByNumber builds query with blockNumber and validator criteria`() {
        val validator = Address("0x1234567890abcdef1234567890abcdef12345678")
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(
                validatorBlock(
                    blockNumber = 12345,
                    status = BlockStatus.VALIDATED,
                    validator = validator.value.lowercase(),
                )
            )

        val result = service.getBlockByNumber(12345, validator)

        expectThat(result).hasSize(1)
        val andList = querySlot.captured.queryObject.get("\$and") as List<*>
        expectThat(andList).hasSize(2)
        expectThat(andList[0]).isEqualTo(org.bson.Document("blockNumber", 12345L))
        expectThat(andList[1])
            .isEqualTo(org.bson.Document("validator", validator.value.lowercase()))
    }

    @Test
    fun `getBlockByNumber returns empty list when no records found`() {
        every { mongoTemplate.find(any<Query>(), ValidatorBlock::class.java) } returns emptyList()

        val result = service.getBlockByNumber(99999, null)

        expectThat(result).isEmpty()
    }

    // -- getValidators hasNext tests --

    @Test
    fun `getValidators hasNext is false when results equal pageSize`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "validatorTvl"))
        val validators =
            (1..3).map { validator(id = "0x000000000000000000000000000000000000000$it") }

        every { mongoTemplate.find(any<Query>(), Validator::class.java) } returns validators

        val result = service.getValidators(null, null, null, pageable)

        expectThat(result.content).hasSize(3)
        expectThat(result.hasNext()).isFalse()
    }

    @Test
    fun `getValidators hasNext is true and content is trimmed when more results exist`() {
        val pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "validatorTvl"))
        val validators =
            (1..4).map { validator(id = "0x000000000000000000000000000000000000000$it") }

        every { mongoTemplate.find(any<Query>(), Validator::class.java) } returns validators

        val result = service.getValidators(null, null, null, pageable)

        expectThat(result.content).hasSize(3)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `getValidators hasNext is false when fewer results than pageSize`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "validatorTvl"))
        val validators =
            (1..2).map { validator(id = "0x000000000000000000000000000000000000000$it") }

        every { mongoTemplate.find(any<Query>(), Validator::class.java) } returns validators

        val result = service.getValidators(null, null, null, pageable)

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isFalse()
    }

    // -- getValidatorById tests --

    @Test
    fun `getValidatorById builds query with _id criteria`() {
        val querySlot = slot<Query>()
        val validator = validator(id = "0x1234567890abcdef1234567890abcdef12345678")

        every { mongoTemplate.findOne(capture(querySlot), Validator::class.java) } returns validator

        val result = service.getValidatorById("0x1234567890abcdef1234567890abcdef12345678")

        expectThat(result).isNotNull().and {
            get { id }.isEqualTo("0x1234567890abcdef1234567890abcdef12345678")
        }
        expectThat(querySlot.captured.queryObject)
            .isEqualTo(org.bson.Document("_id", "0x1234567890abcdef1234567890abcdef12345678"))
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
