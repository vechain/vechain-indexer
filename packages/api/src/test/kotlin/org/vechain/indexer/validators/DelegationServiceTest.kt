package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationCountAggregateResult
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.DelegationStatusCount
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class DelegationServiceTest {
    private val repository: DelegationRepository = mockk()
    private val service = DelegationService(repository)

    private val pageable = PageRequest.of(0, 20)
    private val emptySlice: Slice<Delegation> = SliceImpl(emptyList())

    private val validatorMixedCase = "0xABCDef0123456789ABCDef0123456789abcdef00"
    private val normalisedValidator = "0xabcdef0123456789abcdef0123456789abcdef00"
    private val tokenId = "42"
    private val statuses = listOf(DelegationStatus.ACTIVE, DelegationStatus.QUEUED)

    @Test
    fun `getDelegations with no filters calls findAll`() {
        every { repository.findAll(pageable) } returns emptySlice

        service.getDelegations(null, null, null, pageable)

        verify(exactly = 1) { repository.findAll(pageable) }
    }

    @Test
    fun `getDelegations with validator only calls findByValidator and normalises the address`() {
        every { repository.findByValidator(normalisedValidator, pageable) } returns emptySlice

        service.getDelegations(validatorMixedCase, null, null, pageable)

        verify(exactly = 1) { repository.findByValidator(normalisedValidator, pageable) }
    }

    @Test
    fun `getDelegations with tokenId only calls findByTokenId`() {
        every { repository.findByTokenId(tokenId, pageable) } returns emptySlice

        service.getDelegations(null, tokenId, null, pageable)

        verify(exactly = 1) { repository.findByTokenId(tokenId, pageable) }
    }

    @Test
    fun `getDelegations with statuses only calls findByStatusIn`() {
        every { repository.findByStatusIn(statuses, pageable) } returns emptySlice

        service.getDelegations(null, null, statuses, pageable)

        verify(exactly = 1) { repository.findByStatusIn(statuses, pageable) }
    }

    @Test
    fun `getDelegations with validator and tokenId calls combined query and normalises the address`() {
        every {
            repository.findByValidatorAndTokenId(normalisedValidator, tokenId, pageable)
        } returns emptySlice

        service.getDelegations(validatorMixedCase, tokenId, null, pageable)

        verify(exactly = 1) {
            repository.findByValidatorAndTokenId(normalisedValidator, tokenId, pageable)
        }
    }

    @Test
    fun `getDelegations with validator and statuses calls combined query and normalises the address`() {
        every {
            repository.findByValidatorAndStatusIn(normalisedValidator, statuses, pageable)
        } returns emptySlice

        service.getDelegations(validatorMixedCase, null, statuses, pageable)

        verify(exactly = 1) {
            repository.findByValidatorAndStatusIn(normalisedValidator, statuses, pageable)
        }
    }

    @Test
    fun `getDelegations with tokenId and statuses calls combined query`() {
        every { repository.findByTokenIdAndStatusIn(tokenId, statuses, pageable) } returns
            emptySlice

        service.getDelegations(null, tokenId, statuses, pageable)

        verify(exactly = 1) { repository.findByTokenIdAndStatusIn(tokenId, statuses, pageable) }
    }

    @Test
    fun `getDelegations with all three filters calls the most specific query`() {
        every {
            repository.findByValidatorAndTokenIdAndStatusIn(
                normalisedValidator,
                tokenId,
                statuses,
                pageable,
            )
        } returns emptySlice

        service.getDelegations(validatorMixedCase, tokenId, statuses, pageable)

        verify(exactly = 1) {
            repository.findByValidatorAndTokenIdAndStatusIn(
                normalisedValidator,
                tokenId,
                statuses,
                pageable,
            )
        }
    }

    @Test
    fun `getDelegationCounts without validator aggregates across all validators`() {
        val results =
            listOf(
                DelegationCountAggregateResult(
                    _id = normalisedValidator,
                    counts =
                        listOf(
                            DelegationStatusCount("QUEUED", 3L),
                            DelegationStatusCount("ACTIVE", 7L),
                            DelegationStatusCount("EXITING", 2L),
                        ),
                )
            )
        every { repository.aggregateDelegationCountsByValidator() } returns results

        val response = service.getDelegationCounts(null)

        expectThat(response).hasSize(1)
        expectThat(response[0])
            .isEqualTo(
                DelegationCountsResponse(
                    validator = normalisedValidator,
                    queued = 3L,
                    active = 7L,
                    exiting = 2L,
                )
            )
        verify(exactly = 1) { repository.aggregateDelegationCountsByValidator() }
    }

    @Test
    fun `getDelegationCounts with validator address lowercases it before querying`() {
        every { repository.aggregateDelegationCountsByValidator(normalisedValidator) } returns
            emptyList()

        service.getDelegationCounts(Address(validatorMixedCase))

        verify(exactly = 1) { repository.aggregateDelegationCountsByValidator(normalisedValidator) }
    }

    @Test
    fun `getDelegationCounts defaults missing status counts to zero`() {
        val results =
            listOf(
                DelegationCountAggregateResult(
                    _id = normalisedValidator,
                    counts = listOf(DelegationStatusCount("ACTIVE", 5L)),
                )
            )
        every { repository.aggregateDelegationCountsByValidator() } returns results

        val response = service.getDelegationCounts(null)

        expectThat(response[0])
            .isEqualTo(
                DelegationCountsResponse(
                    validator = normalisedValidator,
                    queued = 0L,
                    active = 5L,
                    exiting = 0L,
                )
            )
    }
}
