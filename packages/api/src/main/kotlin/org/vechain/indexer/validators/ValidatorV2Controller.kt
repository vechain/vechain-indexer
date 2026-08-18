package org.vechain.indexer.validators

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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
import org.vechain.indexer.rest.PaginatedResponse
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
@Tag(name = "Validator", description = "Query validator documents")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH_V2)
open class ValidatorV2Controller(
    private val mongoTemplate: MongoTemplate,
    private val aggregateService: ValidatorAggregateService,
    private val priceFeedService: PriceFeedService,
    private val service: ValidatorService,
) {

    @GetMapping
    @Operation(
        summary = "Get V2 validators with optional filters",
        description =
            "Returns validators from the V2 indexer. TVL / yield / NFT-yield fields require " +
                "VET and VTHO USD prices from the vechain.energy `PriceFeedOracle`; if the " +
                "oracle is unavailable the endpoint returns 503 rather than a half-populated " +
                "response. `online` and `totalRewards` are not yet wired up — see " +
                "`ValidatorV2Response` for the remaining formulas.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Filter by one or more validator statuses",
        required = false,
    )
    @AddressParameter(name = "endorser", description = "Filter by endorser address")
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    @PaginationParameters
    open fun getValidators(
        @RequestParam(required = false) status: List<Status>?,
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorV2Response> {
        val pageable =
            withIdTieBreaker(toPageable(page, size, direction, Validator::validatorVetStaked.name))

        val criteria = mutableListOf<Criteria>()
        status?.let { criteria += Criteria.where(Validator::status.name).`in`(it) }
        endorser?.let {
            criteria += Criteria.where(Validator::endorser.name).`is`(HexUtils.normalise(it))
        }

        val query =
            if (criteria.isNotEmpty()) Query(Criteria().andOperator(*criteria.toTypedArray()))
            else Query()
        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find<Validator>(query)
        val hasNext = results.size > pageable.pageSize
        val pageContent = if (hasNext) results.dropLast(1) else results

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
        summary = "Get per-validator slot accounting over a time range",
        description =
            "Returns each validator's slot accounting across the requested timestamp window " +
                "(inclusive, Unix seconds). `missedSlotRatio = missedSlots / (proposedBlocks + " +
                "missedSlots)`. Validators with no scheduled slots in the window are absent from " +
                "the response.",
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @CommonApiResponses
    open fun getSlotStats(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): List<ValidatorSlotStats> = service.getSlotStats(startTimestamp, endTimestamp)

    @GetMapping("/{validatorId}/slots")
    @Operation(
        summary = "Get a single validator's slot accounting over a time range",
        description =
            "Returns one validator's slot accounting across the requested timestamp window " +
                "(inclusive, Unix seconds). Returns zeroed counts when the validator had no " +
                "scheduled slots in the window.",
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @CommonApiResponses
    open fun getSlotStatsForValidator(
        @PathVariable @ValidAddress validatorId: Address,
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): ValidatorSlotStats {
        val normalised = HexUtils.normalise(validatorId.value)
        return service.getSlotStatsForValidator(startTimestamp, endTimestamp, normalised)
            ?: ValidatorSlotStats(
                validator = normalised,
                proposedBlocks = 0L,
                missedSlots = 0L,
                missedSlotRatio = 0.0,
            )
    }

    @GetMapping("/{validatorId}")
    @Operation(
        summary = "Get a single V2 validator by ID",
        description = "Returns one validator's V2 stats by their address.",
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    open fun getValidatorById(
        @PathVariable @ValidAddress validatorId: Address
    ): ValidatorV2Response {
        val normalised = HexUtils.normalise(validatorId.value)
        val doc =
            mongoTemplate.findOne<Validator>(Query(Criteria.where("_id").`is`(normalised)))
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
