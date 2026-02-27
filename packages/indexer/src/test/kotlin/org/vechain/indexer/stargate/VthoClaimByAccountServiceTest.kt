package org.vechain.indexer.stargate

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.WriteModel
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccount
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountService
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class VthoClaimByAccountServiceTest {
    @MockK lateinit var repository: VthoClaimedByAccountRepository

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate
    @MockK(relaxed = true) lateinit var mongoCollection: MongoCollection<Document>
    @MockK(relaxed = true) lateinit var converter: MongoConverter
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: VthoClaimedByAccountService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { mongoTemplate.getCollectionName(any<Class<*>>()) } returns "test_collection"
        every { mongoTemplate.getCollection(any()) } returns mongoCollection
        every { mongoTemplate.converter } returns converter
        service = VthoClaimedByAccountService(repository, mongoTemplate, inlineVersioningProperties)
    }

    @Test
    fun `parseAccountRecords returns empty list for empty events`() {
        val result = service.parseAccountRecords(emptyList(), emptyList())
        expect { that(result.size).isEqualTo(0) }
    }

    @Test
    fun `parseAccountTokenIdRecords returns empty list for empty events`() {
        val result = service.parseAccountTokenIdRecords(emptyList(), emptyList())
        expect { that(result.size).isEqualTo(0) }
    }

    @Test
    fun `parseAccountRecords creates new records for new accounts`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("100")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("200")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 13L
                    every { blockTimestamp } returns 1300L
                    every { params.getAsString("owner") } returns "0xdef"
                    every { params.getAsBigInteger("value") } returns BigInteger("50")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
            )
        val result = service.parseAccountRecords(events, emptyList())
        expect {
            that(result.size).isEqualTo(2)
            val abc = result.find { it.account == "0xabc" }
            val def = result.find { it.account == "0xdef" }
            that(abc?.total).isEqualTo(BigInteger("300"))
            that(abc?.version).isEqualTo(1)
            that(abc?.blockId).isEqualTo("block3")
            that(abc?.blockNumber).isEqualTo(13L)
            that(abc?.blockTimestamp).isEqualTo(1300L)
            that(def?.total).isEqualTo(BigInteger("50"))
            that(def?.version).isEqualTo(1)
        }
    }

    @Test
    fun `parseAccountTokenIdRecords creates new records for new account token id pair`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsString("tokenId") } returns "123"
                    every { params.getAsBigInteger("value") } returns BigInteger("100")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsString("tokenId") } returns "123"
                    every { params.getAsBigInteger("value") } returns BigInteger("200")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsString("tokenId") } returns "456"
                    every { params.getAsBigInteger("value") } returns BigInteger("200")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 13L
                    every { blockTimestamp } returns 1300L
                    every { params.getAsString("owner") } returns "0xdef"
                    every { params.getAsString("tokenId") } returns "987"
                    every { params.getAsBigInteger("value") } returns BigInteger("50")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
            )
        val result =
            service.parseAccountTokenIdRecords(
                events,
                listOf(
                    VthoClaimedByAccount(
                        blockId = "block3",
                        blockNumber = 13L,
                        blockTimestamp = 1300L,
                        total = BigInteger("300"),
                        account = "0xdef",
                        version = 1,
                        legacyRewards = BigInteger.ZERO,
                        delegationRewards = BigInteger("300"),
                        tokenId = "987",
                        id = "0xdef_987",
                    )
                ),
            )
        expect {
            that(result.size).isEqualTo(3)
            val abc123 = result.find { it.account == "0xabc" && it.tokenId == "123" }
            val abc456 = result.find { it.account == "0xabc" && it.tokenId == "456" }
            val def987 = result.find { it.account == "0xdef" && it.tokenId == "987" }
            that(abc123?.total).isEqualTo(BigInteger("300"))
            that(abc123?.version).isEqualTo(1)
            that(abc123?.blockId).isEqualTo("block3")
            that(abc123?.blockNumber).isEqualTo(13L)
            that(abc123?.blockTimestamp).isEqualTo(1300L)
            that(abc456?.total).isEqualTo(BigInteger("200"))
            that(def987?.version).isEqualTo(2)
            that(def987?.total).isEqualTo(BigInteger("350"))
            that(def987?.legacyRewards).isEqualTo(BigInteger("0"))
            that(def987?.delegationRewards).isEqualTo(BigInteger("350"))
        }
    }

    @Test
    fun `parseAccountRecords increments version and adds to previous total`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block4"
                    every { blockNumber } returns 20L
                    every { blockTimestamp } returns 2000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("10")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                }
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    blockId = "block3",
                    blockNumber = 13L,
                    blockTimestamp = 1300L,
                    total = BigInteger("300"),
                    account = "0xabc",
                    version = 1,
                    legacyRewards = BigInteger.ZERO,
                    delegationRewards = BigInteger("300"),
                    tokenId = null,
                    id = "0xabc",
                )
            )
        val result = service.parseAccountRecords(events, existing)
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
            that(result[0].total).isEqualTo(BigInteger("310"))
            that(result[0].version).isEqualTo(2)
            that(result[0].blockId).isEqualTo("block4")
            that(result[0].blockNumber).isEqualTo(20L)
            that(result[0].blockTimestamp).isEqualTo(2000L)
        }
    }

    @Test
    fun `parseAccountTokenIdRecords increments version and adds to previous total`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block4"
                    every { blockNumber } returns 20L
                    every { blockTimestamp } returns 2000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsString("tokenId") } returns "123"
                    every { params.getAsBigInteger("value") } returns BigInteger("10")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                }
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    blockId = "block3",
                    blockNumber = 13L,
                    blockTimestamp = 1300L,
                    total = BigInteger("300"),
                    account = "0xabc",
                    version = 1,
                    legacyRewards = BigInteger.ZERO,
                    delegationRewards = BigInteger("300"),
                    tokenId = "123",
                    id = "0xabc_123",
                )
            )
        val result = service.parseAccountTokenIdRecords(events, existing)
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
            that(result[0].total).isEqualTo(BigInteger("310"))
            that(result[0].version).isEqualTo(2)
            that(result[0].blockId).isEqualTo("block4")
            that(result[0].blockNumber).isEqualTo(20L)
            that(result[0].blockTimestamp).isEqualTo(2000L)
            that(result[0].tokenId).isEqualTo("123")
        }
    }

    @Test
    fun `update saves update and archives existing`() {
        val update =
            listOf(
                VthoClaimedByAccount(
                    1,
                    "block1",
                    1L,
                    1000L,
                    BigInteger("1"),
                    BigInteger("0"),
                    BigInteger("1"),
                    "0xabc",
                    tokenId = null,
                    id = "0xabc",
                )
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    0,
                    "block0",
                    0L,
                    900L,
                    BigInteger("0"),
                    BigInteger("0"),
                    BigInteger("0"),
                    "0xabc",
                    tokenId = null,
                    id = "0xabc",
                )
            )

        service.save(update, existing)

        verify(exactly = 1) {
            mongoCollection.bulkWrite(any<List<WriteModel<Document>>>(), any<BulkWriteOptions>())
        }
    }

    @Test
    fun `update does not save or archive empty lists`() {
        val update = emptyList<VthoClaimedByAccount>()
        val existing = emptyList<VthoClaimedByAccount>()

        service.save(update, existing)

        verify(exactly = 0) {
            mongoCollection.bulkWrite(any<List<WriteModel<Document>>>(), any<BulkWriteOptions>())
        }
    }

    @Test
    fun `getExistingByAccount fetches by owner addresses`() {
        val events =
            listOf(
                mockk<IndexedEvent> { every { params.getAsString("owner") } returns "0xabc" },
                mockk<IndexedEvent> { every { params.getAsString("owner") } returns "0xdef" },
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    1,
                    "block1",
                    1L,
                    1000L,
                    BigInteger("1"),
                    BigInteger("0"),
                    BigInteger("1"),
                    "0xabc",
                    tokenId = null,
                    id = "0xabc",
                )
            )
        every {
            repository.findAllById(listOf(IdUtils.generateId("0xabc"), IdUtils.generateId("0xdef")))
        } returns existing

        val result = service.getExistingByAccount(events)

        verify(exactly = 1) {
            repository.findAllById(listOf(IdUtils.generateId("0xabc"), IdUtils.generateId("0xdef")))
        }
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
        }
    }

    @Test
    fun `getExistingByAccountTokenId fetches by owner addresses and token id`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsString("tokenId") } returns "123"
                },
                mockk<IndexedEvent> {
                    every { params.getAsString("owner") } returns "0xdef"
                    every { params.getAsString("tokenId") } returns "456"
                },
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    1,
                    "block1",
                    1L,
                    1000L,
                    BigInteger("1"),
                    BigInteger("0"),
                    BigInteger("1"),
                    "0xabc",
                    tokenId = "123",
                    id = "0xabc_123",
                )
            )
        every {
            repository.findAllById(
                listOf(IdUtils.generateId("0xabc", "123"), IdUtils.generateId("0xdef", "456"))
            )
        } returns existing

        val result = service.getExistingByAccountTokenId(events)

        verify(exactly = 1) {
            repository.findAllById(
                listOf(IdUtils.generateId("0xabc", "123"), IdUtils.generateId("0xdef", "456"))
            )
        }
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
            that(result[0].tokenId).isEqualTo("123")
        }
    }
}
