package org.vechain.indexer.explorer

import com.mongodb.MongoClientSettings
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.WriteModel
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.util.Optional
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
class AverageFeesPerUserServiceTest {
    @MockK lateinit var repository: AverageFeesPerUserRepository
    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var mongoConverter: MongoConverter

    private val service by lazy {
        val inlineVersioningProperties =
            InlineVersioningProperties().apply {
                blockWindow = 10_000
                maxVersions = 100
            }
        AverageFeesPerUserService(
            repository = repository,
            mongoTemplate = mongoTemplate,
            inlineVersioningProperties = inlineVersioningProperties,
        )
    }

    @Test
    fun `processBlock creates marker records and a versioned daily summary`() {
        val block =
            block(
                number = 100L,
                timestamp = 1_704_067_200L,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x2", origin = "0xAA", paid = "0x1bc16d674ec80000"),
                        tx(id = "0x3", origin = "0xBB", paid = "0x29a2241af62c0000"),
                    ),
            )

        every { repository.findById("summary-2024-01-01") } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        val update = requireNotNull(service.processBlock(block))

        assertEquals(2, update.newMarkers.size)
        assertEquals(
            setOf("origin-2024-01-01-0xaa", "origin-2024-01-01-0xbb"),
            update.newMarkers.map { it.id }.toSet(),
        )
        assertEquals("summary-2024-01-01", update.updatedSummary.id)
        assertEquals(1, update.updatedSummary.version)
        assertDecimalEquals("6", update.updatedSummary.totalFeesPaid!!)
        assertEquals(2L, update.updatedSummary.dailyActiveUsers)
        assertDecimalEquals("3", update.updatedSummary.averageFeesPerUser!!)
        assertEquals(null, update.existingSummary)
    }

    @Test
    fun `processBlock updates the same day without recounting known origins`() {
        val block =
            block(
                number = 101L,
                timestamp = 1_704_067_260L,
                transactions =
                    listOf(
                        tx(id = "0x4", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x5", origin = "0xCC", paid = "0x1bc16d674ec80000"),
                    ),
            )
        val existingSummary =
            AverageFeesPerUser(
                id = "summary-2024-01-01",
                blockId = "0xold",
                blockNumber = 100L,
                blockTimestamp = 1_704_067_200L,
                version = 4,
                recordType = AverageFeesPerUserRecordType.SUMMARY,
                date = "2024-01-01",
                dayStartTimestamp = service.getDayStartTimestamp(block.timestamp),
                totalFeesPaid = decimal("4"),
                dailyActiveUsers = 2L,
                averageFeesPerUser = decimal("2"),
            )

        every { repository.findById("summary-2024-01-01") } returns Optional.of(existingSummary)
        every { repository.findAllById(any<Iterable<String>>()) } returns
            listOf(marker(date = "2024-01-01", origin = "0xaa", blockNumber = 100L))

        val update = requireNotNull(service.processBlock(block))

        assertEquals(1, update.newMarkers.size)
        assertEquals("origin-2024-01-01-0xcc", update.newMarkers.single().id)
        assertEquals("summary-2024-01-01", update.updatedSummary.id)
        assertEquals(5, update.updatedSummary.version)
        assertDecimalEquals("7", update.updatedSummary.totalFeesPaid!!)
        assertEquals(3L, update.updatedSummary.dailyActiveUsers)
        assertDecimalEquals("2.333333333333", update.updatedSummary.averageFeesPerUser!!)
        assertEquals(existingSummary, update.existingSummary)
    }

    @Test
    fun `processBlock counts the same origin again on a new utc day`() {
        val block =
            block(
                number = 200L,
                timestamp = 1_704_153_600L,
                transactions = listOf(tx(id = "0x6", origin = "0xAA", paid = "0xde0b6b3a7640000")),
            )

        every { repository.findById("summary-2024-01-02") } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        val update = requireNotNull(service.processBlock(block))

        assertEquals(1, update.newMarkers.size)
        assertEquals("origin-2024-01-02-0xaa", update.newMarkers.single().id)
        assertEquals("summary-2024-01-02", update.updatedSummary.id)
        assertEquals(1, update.updatedSummary.version)
        assertEquals(1L, update.updatedSummary.dailyActiveUsers)
        assertDecimalEquals("1", update.updatedSummary.averageFeesPerUser!!)
    }

    @Test
    fun `save persists markers and versioned summary using inline versioning settings`() {
        val collection = mockk<MongoCollection<Document>>()
        val writesSlot = slot<List<WriteModel<Document>>>()
        every { repository.saveAll(any<List<AverageFeesPerUser>>()) } returns emptyList()
        every { mongoTemplate.getCollectionName(AverageFeesPerUser::class.java) } returns
            "averageFees"
        every { mongoTemplate.getCollection("averageFees") } returns collection
        every { mongoTemplate.converter } returns mongoConverter
        every { mongoConverter.write(any<AverageFeesPerUser>(), any<Document>()) } answers
            {
                val source = firstArg<AverageFeesPerUser>()
                val target = secondArg<Document>()
                target["_id"] = source.id
                target["blockNumber"] = source.blockNumber
                target["version"] = source.version
                source.dayStartTimestamp?.let { target["dayStartTimestamp"] = it }
                source.totalFeesPaid?.let { target["totalFeesPaid"] = it.toPlainString() }
                source.dailyActiveUsers?.let { target["dailyActiveUsers"] = it }
                source.averageFeesPerUser?.let { target["averageFeesPerUser"] = it.toPlainString() }
            }
        every { collection.bulkWrite(capture(writesSlot), any()) } returns
            mockk<BulkWriteResult>(relaxed = true)

        val update =
            AverageFeesPerUserBlockUpdate(
                newMarkers =
                    listOf(marker(date = "2024-01-01", origin = "0xaa", blockNumber = 100L)),
                updatedSummary =
                    summary(
                        id = "summary-2024-01-01",
                        version = 2,
                        totalFeesPaid = "3",
                        dailyActiveUsers = 1L,
                        averageFeesPerUser = "3",
                    ),
                existingSummary =
                    summary(
                        id = "summary-2024-01-01",
                        version = 1,
                        totalFeesPaid = "1",
                        dailyActiveUsers = 1L,
                        averageFeesPerUser = "1",
                    ),
            )

        service.save(update)

        verify(exactly = 1) { repository.saveAll(update.newMarkers) }
        verify(exactly = 1) { collection.bulkWrite(any<List<WriteModel<Document>>>(), any()) }

        val updateModel = writesSlot.captured.single() as UpdateOneModel<Document>
        val pipeline =
            updateModel.updatePipeline!!.map {
                it.toBsonDocument(
                    Document::class.java,
                    MongoClientSettings.getDefaultCodecRegistry(),
                )
            }
        val pipelineText = pipeline.joinToString("\n") { it.toJson() }

        assertTrue(pipelineText.contains("\"_previousVersions\""))
        assertTrue(pipelineText.contains("9900"))
        assertTrue(pipelineText.contains("\"\$slice\": ["))
        assertTrue(pipelineText.contains("100"))
    }

    @Test
    fun `save skips marker persistence when no new markers are present`() {
        val collection = mockk<MongoCollection<Document>>()
        val writesSlot = slot<List<WriteModel<Document>>>()
        every { mongoTemplate.getCollectionName(AverageFeesPerUser::class.java) } returns
            "averageFees"
        every { mongoTemplate.getCollection("averageFees") } returns collection
        every { mongoTemplate.converter } returns mongoConverter
        every { mongoConverter.write(any<AverageFeesPerUser>(), any<Document>()) } answers
            {
                val source = firstArg<AverageFeesPerUser>()
                val target = secondArg<Document>()
                target["_id"] = source.id
                target["blockNumber"] = source.blockNumber
                target["version"] = source.version
            }
        every { collection.bulkWrite(capture(writesSlot), any()) } returns
            mockk<BulkWriteResult>(relaxed = true)

        val update =
            AverageFeesPerUserBlockUpdate(
                newMarkers = emptyList(),
                updatedSummary =
                    summary(
                        id = "summary-2024-01-02",
                        version = 1,
                        totalFeesPaid = "2",
                        dailyActiveUsers = 1L,
                        averageFeesPerUser = "2",
                    ),
                existingSummary = null,
            )

        service.save(update)

        verify(exactly = 0) { repository.saveAll(any<List<AverageFeesPerUser>>()) }
        verify(exactly = 1) { collection.bulkWrite(any<List<WriteModel<Document>>>(), any()) }

        val updateModel = writesSlot.captured.single() as UpdateOneModel<Document>
        assertEquals(1, updateModel.updatePipeline!!.size)
    }

    private fun decimal(value: String): BigDecimal = BigDecimal(value)

    private fun assertDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun marker(date: String, origin: String, blockNumber: Long) =
        AverageFeesPerUser(
            id = "origin-$date-$origin",
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = 1_704_067_200L,
            version = 1,
            recordType = AverageFeesPerUserRecordType.ORIGIN_MARKER,
            date = date,
            origin = origin,
        )

    private fun summary(
        id: String,
        version: Int,
        totalFeesPaid: String,
        dailyActiveUsers: Long,
        averageFeesPerUser: String,
    ) =
        AverageFeesPerUser(
            id = id,
            blockId = "0xsummary",
            blockNumber = 100L,
            blockTimestamp = 1_704_067_200L,
            version = version,
            recordType = AverageFeesPerUserRecordType.SUMMARY,
            date = id.removePrefix("summary-"),
            dayStartTimestamp = 1_704_067_200L,
            totalFeesPaid = decimal(totalFeesPaid),
            dailyActiveUsers = dailyActiveUsers,
            averageFeesPerUser = decimal(averageFeesPerUser),
        )

    private fun block(number: Long, timestamp: Long, transactions: List<Transaction>) =
        Block(
            id = "0x${number.toString(16)}",
            number = number,
            timestamp = timestamp,
            parentID = "0xparent",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xbeneficiary",
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0xtxsroot",
            txsFeatures = 0,
            stateRoot = "0xstateroot",
            receiptsRoot = "0xreceiptsroot",
            signer = "0xsigner",
            isTrunk = true,
            isFinalized = true,
            transactions = transactions,
            com = false,
        )

    private fun tx(id: String, origin: String, paid: String) =
        Transaction(
            id = id,
            reward = "0x0",
            chainTag = 1,
            blockRef = "0x00",
            expiration = 720,
            clauses = listOf(mockk<Clause>(relaxed = true)),
            gasPriceCoef = 0,
            gas = 21_000,
            maxFeePerGas = "0x0",
            maxPriorityFeePerGas = "0x0",
            origin = origin,
            delegator = null,
            nonce = "0x1",
            dependsOn = null,
            size = 100,
            gasUsed = 21_000,
            gasPayer = origin,
            paid = paid,
            outputs = emptyList(),
            reverted = false,
            type = 1,
        )
}
