package org.vechain.indexer.validator

import io.mockk.*
import org.bson.types.Decimal128
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.ThorService
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(SpringExtension::class)
class ValidatorServiceTest {
    private lateinit var repository: ValidatorRepository
    private lateinit var archiveService: ArchiveService<Validator, ValidatorArchive>
    private lateinit var thorService: ThorService
    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        archiveService = mockk(relaxed = true)
        thorService = mockk(relaxed = true)
        service = ValidatorService(repository, archiveService, thorService, "0xcontract")
    }

    @Test
    fun `applyDelegation should increment delegation counts`() {
        val validator =
            Validator(
                id = "v1",
                blockId = "b1",
                blockNumber = 1,
                blockTimestamp = 1000,
                delegationIds = mapOf("5" to TokenLevel.Mjolnir),
                delegations = mapOf(TokenLevel.All to 1L, TokenLevel.Mjolnir to 1L),
                delegationIdList = listOf("5"),
                totalVTHOSupply = Decimal128(1),
                version = 1,
            )

        val event =
            IndexedEvent(
                id = "evt1",
                blockId = "b1",
                blockNumber = 1,
                blockTimestamp = 1000,
                txId = "tx1",
                origin = "origin1",
                paid = null,
                gasUsed = null,
                gasPayer = null,
                raw = null,
                params = AbiEventParameters(returnValues = mapOf("delegationID" to 5L)),
                address = "0xcontract",
                eventType = "DelegationAdded",
                clauseIndex = 0,
                signature = null,
            )

        val updated = service.applyDelegation(validator, event)

        expectThat(updated.delegations[TokenLevel.All]).isEqualTo(2L)
        expectThat(updated.delegations[TokenLevel.Mjolnir]).isEqualTo(2L)
        expectThat(updated.version).isEqualTo(2)
    }

    @Test
    fun `removeDelegation should decrement delegation counts`() {
        val validator =
            Validator(
                id = "v1",
                blockId = "b1",
                blockNumber = 1,
                blockTimestamp = 1000,
                delegationIds = mapOf("5" to TokenLevel.Mjolnir, "6" to TokenLevel.Mjolnir),
                delegations = mapOf(TokenLevel.All to 2L, TokenLevel.Mjolnir to 2L),
                delegationIdList = listOf("5"),
                totalVTHOSupply = Decimal128(1),
                version = 1,
            )

        val event =
            IndexedEvent(
                id = "evt2",
                blockId = "b1",
                blockNumber = 2,
                blockTimestamp = 2000,
                txId = "tx2",
                origin = "origin1",
                paid = null,
                gasUsed = null,
                gasPayer = null,
                raw = null,
                params = AbiEventParameters(returnValues = mapOf("delegationID" to 5L)),
                address = "0xcontract",
                eventType = "DelegationWithdrawn",
                clauseIndex = 0,
                signature = null,
            )

        val updated = service.removeDelegation(validator, event)

        expectThat(updated.delegations[TokenLevel.All]).isEqualTo(1L)
        expectThat(updated.delegations[TokenLevel.Mjolnir]).isEqualTo(1L)
        expectThat(updated.delegationIds.containsKey("5")).isFalse()
        expectThat(updated.version).isEqualTo(2)
    }

    @Test
    fun `handleValidatorEvents should process initiation, apply, and remove`() {
        val initiated =
            IndexedEvent(
                id = "evt1",
                blockId = "b1",
                blockNumber = 1,
                blockTimestamp = 1000,
                txId = "tx1",
                origin = "origin1",
                paid = null,
                gasUsed = null,
                gasPayer = null,
                raw = null,
                params =
                    AbiEventParameters(
                        returnValues = mapOf("validator" to "v1", "delegationId" to 5L)
                    ),
                address = "0xcontract",
                eventType = "DelegationInitiated",
                clauseIndex = 0,
                signature = null,
            )

        val applied =
            IndexedEvent(
                id = "evt2",
                blockId = "b1",
                blockNumber = 2,
                blockTimestamp = 2000,
                txId = "tx2",
                origin = "origin1",
                paid = null,
                gasUsed = null,
                gasPayer = null,
                raw = null,
                params =
                    AbiEventParameters(
                        returnValues = mapOf("delegationID" to 5L, "validator" to "v1")
                    ),
                address = "0xcontract",
                eventType = "DelegationAdded",
                clauseIndex = 0,
                signature = null,
            )

        val withdrawn =
            IndexedEvent(
                id = "evt3",
                blockId = "b1",
                blockNumber = 3,
                blockTimestamp = 3000,
                txId = "tx3",
                origin = "origin1",
                paid = null,
                gasUsed = null,
                gasPayer = null,
                raw = null,
                params = AbiEventParameters(returnValues = mapOf("delegationID" to 5L)),
                address = "0xcontract",
                eventType = "DelegationWithdrawn",
                clauseIndex = 0,
                signature = null,
            )

        every { repository.findAllById(any<List<String>>()) } returns emptyList()
        every {
            repository.findByIdsOrDelegations(any<List<String>>(), any<List<String>>())
        } returns emptyList()

        service.handleValidatorEvents(listOf(initiated, applied, withdrawn))

        verify { repository.saveAll(any<Collection<Validator>>()) }
    }
}
