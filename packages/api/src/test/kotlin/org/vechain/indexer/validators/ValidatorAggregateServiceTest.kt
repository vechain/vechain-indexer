package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ValidatorAggregateServiceTest {
    private val validatorRepository: ValidatorRepository = mockk()
    private val delegationRepository: DelegationRepository = mockk()
    private val service = ValidatorAggregateService(validatorRepository, delegationRepository)

    @Test
    fun `current-cycle totals exclude queued and exiting validators`() {
        every {
            validatorRepository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))
        } returns
            listOf(
                validator(
                    id = "active",
                    status = Status.ACTIVE,
                    weight = BigDecimal("10"),
                    nextWeight = BigDecimal("11"),
                    validatorVet = BigDecimal("100"),
                ),
                validator(
                    id = "queued",
                    status = Status.QUEUED,
                    weight = BigDecimal("999"),
                    nextWeight = BigDecimal("5"),
                    validatorVet = BigDecimal("999"),
                ),
                validator(
                    id = "exiting",
                    status = Status.EXITING,
                    weight = BigDecimal("999"),
                    nextWeight = BigDecimal("3"),
                    validatorVet = BigDecimal("999"),
                ),
            )

        val aggregates = service.build(emptyList())

        expectThat(aggregates.totalWeight).isEqualTo(BigDecimal("10"))
        expectThat(aggregates.totalActiveVetStaked).isEqualTo(BigDecimal("100"))
    }

    @Test
    fun `next-cycle totals include active, queued and exiting validators`() {
        every {
            validatorRepository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))
        } returns
            listOf(
                validator(
                    id = "active",
                    status = Status.ACTIVE,
                    weight = BigDecimal("10"),
                    nextWeight = BigDecimal("11"),
                    validatorVet = BigDecimal("100"),
                    queuedVet = BigDecimal("5"),
                    exitingVet = BigDecimal("2"),
                ),
                validator(
                    id = "queued",
                    status = Status.QUEUED,
                    nextWeight = BigDecimal("7"),
                    queuedVet = BigDecimal("50"),
                ),
                validator(
                    id = "exiting",
                    status = Status.EXITING,
                    nextWeight = BigDecimal("2"),
                    validatorVet = BigDecimal("80"),
                    exitingVet = BigDecimal("80"),
                ),
            )

        val aggregates = service.build(emptyList())

        // 11 (active) + 7 (queued) + 2 (exiting)
        expectThat(aggregates.totalNextPeriodWeight).isEqualTo(BigDecimal("20"))
        // active:  100 + 5 - 2 = 103
        // queued:  0   + 50 - 0 = 50
        // exiting: 80  + 0 - 80 = 0
        expectThat(aggregates.totalActiveNextCycleVetStaked).isEqualTo(BigDecimal("153"))
    }

    @Test
    fun `single repository call when no validators on page`() {
        every {
            validatorRepository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))
        } returns emptyList()

        service.build(emptyList())

        verify(exactly = 1) {
            validatorRepository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))
        }
        verify(exactly = 0) { delegationRepository.findByValidatorInAndStatusIn(any(), any()) }
    }

    private fun validator(
        id: String,
        status: Status,
        weight: BigDecimal? = null,
        nextWeight: BigDecimal? = null,
        validatorVet: BigDecimal = BigDecimal.ZERO,
        delegatorVet: BigDecimal = BigDecimal.ZERO,
        queuedVet: BigDecimal = BigDecimal.ZERO,
        exitingVet: BigDecimal = BigDecimal.ZERO,
    ): Validator =
        Validator(
            id = id,
            blockId = "0xblk",
            blockNumber = 1,
            blockTimestamp = 1,
            status = status,
            validatorLockedWeight = weight,
            totalNextPeriodWeight = nextWeight,
            validatorVetStaked = validatorVet,
            delegatorVetStaked = delegatorVet,
            queuedVetStaked = queuedVet,
            exitingVetStaked = exitingVet,
        )
}
