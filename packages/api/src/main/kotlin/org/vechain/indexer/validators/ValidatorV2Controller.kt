package org.vechain.indexer.validators

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.SliceImpl
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VALIDATORS_PATH_V2
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.PriceOracleUnavailableResponse
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.prices.PriceFeed
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.PaginationUtils.withIdTieBreaker
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorSlotStats

@Profile("validator")
@Tag(name = "Validator", description = "Validators.")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH_V2)
open class ValidatorV2Controller(
    private val aggregateService: ValidatorAggregateService,
    private val priceFeedService: PriceFeedService,
    private val service: ValidatorService,
) {

    @GetMapping
    @Operation(
        summary = "List validators",
        description =
            "Validators with their stake, yield and status. TVL and yield need live VET and " +
                "VTHO prices; if those are unavailable the request fails with 503 rather than " +
                "returning partial data. `online` and `totalRewards` aren't filled in yet.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Only these statuses.",
        required = false,
    )
    @AddressParameter(name = "endorser", description = "Endorser address.")
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getValidators(
        @RequestParam(required = false) status: List<Status>?,
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorV2Response> {
        val pageable =
            withIdTieBreaker(toPageable(page, size, direction, Validator::validatorVetStaked.name))

        val result =
            service.findValidators(null, endorser?.let { HexUtils.normalise(it) }, status, pageable)
        val hasNext = result.hasNext()
        val pageContent = result.content

        // No rows means no price-dependent fields to compute — skip the oracle hop so an empty
        // page never returns 503 just because the oracle happens to be flaky.
        if (pageContent.isEmpty()) {
            return paginatedResponse(SliceImpl(emptyList(), pageable, hasNext))
        }

        // One aggregate query and one price read per request, shared across every row.
        val aggregates = aggregateService.build(pageContent.map { it.id })
        val prices = priceFeedService.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        val vetPrice = prices.getValue(PriceFeed.VET_USD)
        val vthoPrice = prices.getValue(PriceFeed.VTHO_USD)

        val mapped = pageContent.map {
            ValidatorV2Response.from(it, aggregates, vetPrice, vthoPrice)
        }
        return paginatedResponse(SliceImpl(mapped, pageable, hasNext))
    }

    @GetMapping("/slots")
    @Operation(
        summary = "Get every validator's block production over a time range",
        description =
            """
            How each validator did at producing blocks between `startTimestamp` and `endTimestamp`
            (Unix seconds, both included), lowest uptime first.

            - `missedSlotRatio`: the share of its scheduled slots it missed
            - `uptimeRatio`: the share of the time it was online. Thor stops scheduling a
              validator once it misses a slot, so it counts as offline from then until it next
              produces a block or exits.

            Validators with no scheduled slots are listed only if they were offline during the
            range.
            """,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start time in Unix seconds, included.",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End time in Unix seconds, included.",
        required = true,
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getSlotStats(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): ResponseEntity<List<ValidatorSlotStats>> =
        cachedByAge(endTimestamp, service.getSlotStats(startTimestamp, endTimestamp))

    @GetMapping("/{validatorId}/slots")
    @Operation(
        summary = "Get one validator's block production over a time range",
        description =
            "The same figures as `/slots`, for one validator. A validator with no scheduled " +
                "slots and no downtime in the range gets zero counts and full uptime.",
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address.",
        required = true,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start time in Unix seconds, included.",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End time in Unix seconds, included.",
        required = true,
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getSlotStatsForValidator(
        @PathVariable @ValidAddress validatorId: Address,
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): ResponseEntity<ValidatorSlotStats> {
        val normalised = HexUtils.normalise(validatorId.value)
        val stats =
            service.getSlotStatsForValidator(startTimestamp, endTimestamp, normalised)
                ?: ValidatorSlotStats(
                    validator = normalised,
                    proposedBlocks = 0L,
                    missedSlots = 0L,
                    missedSlotRatio = 0.0,
                    uptimeRatio = 1.0,
                )
        return cachedByAge(endTimestamp, stats)
    }

    @GetMapping("/{validatorId}")
    @Operation(
        summary = "Get a validator",
        description = "One validator's stake, yield and status.",
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address.",
        required = true,
    )
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    @CacheFor(CachePolicy.MINUTE)
    open fun getValidatorById(
        @PathVariable @ValidAddress validatorId: Address
    ): ValidatorV2Response {
        val normalised = HexUtils.normalise(validatorId.value)
        val doc =
            service.findValidator(normalised)
                ?: throw ResourceNotFoundException("Validator V2 not found for id $normalised")

        val aggregates = aggregateService.build(listOf(doc.id))
        val prices = priceFeedService.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        return ValidatorV2Response.from(
            doc,
            aggregates,
            prices.getValue(PriceFeed.VET_USD),
            prices.getValue(PriceFeed.VTHO_USD),
        )
    }
}
