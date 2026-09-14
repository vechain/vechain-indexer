package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.DelegationStatusCounts
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class DelegationServiceTest {
    private val repository: DelegationReadRepository = mockk()
    private val service = DelegationService(repository)

    private val validatorMixedCase = "0xABCDef0123456789ABCDef0123456789abcdef00"
    private val normalisedValidator = "0xabcdef0123456789abcdef0123456789abcdef00"
    private val statuses = listOf(DelegationStatus.ACTIVE, DelegationStatus.QUEUED)

    @Test
    fun `getDelegations normalises the validator, decodes the token id and pages one row past`() {
        every { repository.find(any(), any(), any(), any(), any(), any()) } returns emptyList()

        service.getDelegations(
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by(DESC, "blockNumber")),
        )
        service.getDelegations(
            validatorMixedCase,
            "0x2a",
            statuses,
            PageRequest.of(1, 10, Sort.by(ASC, "blockNumber")),
        )

        verify { repository.find(null, null, null, DESC, 0, 21) }
        verify { repository.find(normalisedValidator, "42", statuses, ASC, 10, 11) }
    }

    @Test
    fun `getDelegationCounts maps the counts and lowercases the validator`() {
        every { repository.countsByValidator(null) } returns
            listOf(DelegationStatusCounts(normalisedValidator, queued = 3, active = 7, exiting = 2))
        every { repository.countsByValidator(normalisedValidator) } returns emptyList()

        val all = service.getDelegationCounts(null)
        val one = service.getDelegationCounts(Address(validatorMixedCase))

        expectThat(all).isEqualTo(listOf(DelegationCountsResponse(normalisedValidator, 3L, 7L, 2L)))
        expectThat(one).isEqualTo(emptyList())
    }
}
