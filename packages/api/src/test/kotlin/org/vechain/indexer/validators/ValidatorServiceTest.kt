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

    // -- getValidatorBlockRewards tests --

    @Test
    fun `getValidatorBlockRewards builds query with no criteria when no filters provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(
                validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED),
                validatorBlock(blockNumber = 99, status = BlockStatus.MISSED),
            )

        val result = service.getValidatorBlockRewards(null, null, pageable)

        expectThat(result.data).hasSize(2)
        expectThat(querySlot.captured.queryObject.isEmpty()).isTrue()
        expectThat(querySlot.captured.sortObject["blockNumber"]).isEqualTo(-1)
    }

    @Test
    fun `getValidatorBlockRewards builds query with status criteria when status provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            listOf(validatorBlock(blockNumber = 100, status = BlockStatus.VALIDATED))

        service.getValidatorBlockRewards(null, BlockStatus.VALIDATED, pageable)

        val criteria = querySlot.captured.queryObject
        expectThat(criteria.get("\$and") as List<*>).hasSize(1)
        expectThat((criteria.get("\$and") as List<*>).first())
            .isEqualTo(org.bson.Document("status", BlockStatus.VALIDATED))
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

        service.getValidatorBlockRewards(validator, null, pageable)

        val criteria = querySlot.captured.queryObject
        expectThat(criteria.get("\$and") as List<*>).hasSize(1)
        expectThat((criteria.get("\$and") as List<*>).first())
            .isEqualTo(org.bson.Document("validator", validator.value.lowercase()))
    }

    @Test
    fun `getValidatorBlockRewards applies ASC sort from pageable`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, null, pageable)

        expectThat(querySlot.captured.sortObject).isEqualTo(org.bson.Document("blockNumber", 1))
    }

    @Test
    fun `getValidatorBlockRewards applies DESC sort from pageable`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockNumber"))
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), ValidatorBlock::class.java) } returns
            emptyList()

        service.getValidatorBlockRewards(null, null, pageable)

        expectThat(querySlot.captured.sortObject).isEqualTo(org.bson.Document("blockNumber", -1))
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
