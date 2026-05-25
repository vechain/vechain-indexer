package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.DelegationLevelFacet
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ValidatorAggregateServiceTest {
    private val chainAggregatesService: ValidatorChainAggregatesService = mockk {
        every { get() } returns
            ChainWideValidatorAggregates(
                totalWeight = BigDecimal.ZERO,
                totalNextPeriodWeight = BigDecimal.ZERO,
                totalActiveVetStaked = BigDecimal.ZERO,
                totalActiveNextCycleVetStaked = BigDecimal.ZERO,
            )
    }
    private val delegationRepository: DelegationRepository = mockk()
    private val service = ValidatorAggregateService(chainAggregatesService, delegationRepository)

    @Test
    fun `chain-wide aggregates copied from the cached service`() {
        every { chainAggregatesService.get() } returns
            ChainWideValidatorAggregates(
                totalWeight = BigDecimal("10"),
                totalNextPeriodWeight = BigDecimal("20"),
                totalActiveVetStaked = BigDecimal("100"),
                totalActiveNextCycleVetStaked = BigDecimal("153"),
            )

        val aggregates = service.build(emptyList())

        expectThat(aggregates.totalWeight).isEqualTo(BigDecimal("10"))
        expectThat(aggregates.totalNextPeriodWeight).isEqualTo(BigDecimal("20"))
        expectThat(aggregates.totalActiveVetStaked).isEqualTo(BigDecimal("100"))
        expectThat(aggregates.totalActiveNextCycleVetStaked).isEqualTo(BigDecimal("153"))
    }

    @Test
    fun `no delegation fetch when no validators on page`() {
        service.build(emptyList())

        verify(exactly = 1) { chainAggregatesService.get() }
        verify(exactly = 0) { delegationRepository.aggregateDelegationFacetsByValidators(any()) }
    }

    @Test
    fun `delegation facets fetched only for validators on the page`() {
        every {
            delegationRepository.aggregateDelegationFacetsByValidators(listOf("a", "b"))
        } returns emptyList()

        service.build(listOf("a", "b"))

        verify(exactly = 1) {
            delegationRepository.aggregateDelegationFacetsByValidators(listOf("a", "b"))
        }
    }

    @Test
    fun `current-cycle effective stake sums ACTIVE plus EXITING facets by tokenLevel`() {
        // ACTIVE × 3 Strength + EXITING × 2 Strength + QUEUED (ignored) + EXITED (ignored)
        every { delegationRepository.aggregateDelegationFacetsByValidators(listOf("v")) } returns
            listOf(
                facet("v", DelegationStatus.ACTIVE, TokenLevel.Strength, count = 3),
                facet("v", DelegationStatus.EXITING, TokenLevel.Strength, count = 2),
                facet(
                    "v",
                    DelegationStatus.QUEUED,
                    TokenLevel.Strength,
                    transitionAtBlock = 50,
                    count = 4,
                ),
            )

        val aggregates = service.build(listOf("v"))

        // 5 × Strength.effectiveStake. Computed via the same BigDecimal ops as production so the
        // scale (effectiveStake = 1_000_000 × 1.5 has scale 1) matches under BigDecimal.equals.
        expectThat(aggregates.currentCycleEffectiveDelegationStake("v"))
            .isEqualTo(TokenLevel.Strength.effectiveStake.multiply(BigDecimal.valueOf(5)))
    }

    @Test
    fun `next-cycle effective stake applies per-validator transitionAtBlock threshold`() {
        // periodPlusTwoBlock = 100.
        // ACTIVE: always counted.
        // QUEUED with transitionAt 80 (≤ 100): counted; QUEUED with 120 (> 100): excluded.
        // EXITING with transitionAt 120 (> 100): counted; EXITING with 80 (≤ 100): excluded.
        every { delegationRepository.aggregateDelegationFacetsByValidators(listOf("v")) } returns
            listOf(
                facet("v", DelegationStatus.ACTIVE, TokenLevel.Strength, count = 1),
                facet(
                    "v",
                    DelegationStatus.QUEUED,
                    TokenLevel.Strength,
                    transitionAtBlock = 80,
                    count = 1,
                ),
                facet(
                    "v",
                    DelegationStatus.QUEUED,
                    TokenLevel.Strength,
                    transitionAtBlock = 120,
                    count = 1,
                ),
                facet(
                    "v",
                    DelegationStatus.EXITING,
                    TokenLevel.Strength,
                    transitionAtBlock = 120,
                    count = 1,
                ),
                facet(
                    "v",
                    DelegationStatus.EXITING,
                    TokenLevel.Strength,
                    transitionAtBlock = 80,
                    count = 1,
                ),
            )

        val aggregates = service.build(listOf("v"))

        // 3 facets pass × Strength.effectiveStake. Scale-matched as above.
        expectThat(aggregates.nextCycleEffectiveDelegationStake("v", periodPlusTwoBlock = 100))
            .isEqualTo(TokenLevel.Strength.effectiveStake.multiply(BigDecimal.valueOf(3)))
    }

    @Test
    fun `current delegated level counts merge ACTIVE and EXITING per level`() {
        every { delegationRepository.aggregateDelegationFacetsByValidators(listOf("v")) } returns
            listOf(
                facet("v", DelegationStatus.ACTIVE, TokenLevel.Strength, count = 3),
                facet("v", DelegationStatus.EXITING, TokenLevel.Strength, count = 2),
                facet("v", DelegationStatus.ACTIVE, TokenLevel.Thunder, count = 1),
                facet(
                    "v",
                    DelegationStatus.QUEUED,
                    TokenLevel.Mjolnir,
                    transitionAtBlock = 10,
                    count = 9,
                ),
            )

        val aggregates = service.build(listOf("v"))

        expectThat(aggregates.currentDelegatedLevelCounts("v"))
            .isEqualTo(mapOf(TokenLevel.Strength to 5L, TokenLevel.Thunder to 1L))
    }

    @Test
    fun `unrecognised status or tokenLevel strings are dropped`() {
        every { delegationRepository.aggregateDelegationFacetsByValidators(listOf("v")) } returns
            listOf(
                DelegationLevelFacet("v", "ACTIVE", "Strength", null, 1),
                DelegationLevelFacet("v", "ACTIVE", "MysteryLevel", null, 99),
                DelegationLevelFacet("v", "UNKNOWN", "Strength", null, 99),
            )

        val aggregates = service.build(listOf("v"))

        // Only the well-formed Strength row contributes.
        expectThat(aggregates.currentDelegatedLevelCounts("v"))
            .isEqualTo(mapOf(TokenLevel.Strength to 1L))
    }

    private fun facet(
        validator: String,
        status: DelegationStatus,
        tokenLevel: TokenLevel,
        transitionAtBlock: Long? = null,
        count: Long,
    ): DelegationLevelFacet =
        DelegationLevelFacet(
            validator = validator,
            status = status.name,
            tokenLevel = tokenLevel.name,
            transitionAtBlock = transitionAtBlock,
            count = count,
        )
}
