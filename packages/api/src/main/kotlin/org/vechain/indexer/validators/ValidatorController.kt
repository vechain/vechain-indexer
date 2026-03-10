package org.vechain.indexer.validator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VALIDATORS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.SortFieldUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTokenId
import org.vechain.indexer.validators.AllValidatorsMissedBlocksResponse
import org.vechain.indexer.validators.DelegationCountsResponse
import org.vechain.indexer.validators.MissedBlocksTimeframe
import org.vechain.indexer.validators.ValidatorService

@Profile("validator")
@Tag(name = "Validator", description = "Query validator documents")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH)
open class ValidatorController(
    private val delegationRepository: DelegationRepository,
    private val service: ValidatorService,
) {
    @GetMapping
    @Operation(
        summary = "Get validators with optional filters",
        description =
            """
            This endpoint retrieves validator stats.

            You can filter the results by:
            - `validatorId`: (deprecated - use GET /api/v1/validators/{validatorId} instead)
            - `status`: validator status
            - `endorser`: endorser address

            You can also sort the results by one of the supported fields and paginate.

            - `sortBy`: Choose between `validatorTvl`, `totalTvl`, `blockProbability`, `delegatorTvl`, or `nft:<Level>` (e.g. `nft:Strength`)
            - `page` and `size`: Controls pagination
            - `direction`: Either `asc` or `desc`
            """,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validatorId",
        description = "Deprecated: use GET /api/v1/validators/{validatorId} instead.",
        required = false,
        deprecated = true,
        schema = Schema(type = "string", pattern = Address.REGEX),
    )
    @AddressParameter(name = "endorser", description = "Filter by endorser address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Filter by one or more validator statuses",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
        required = false,
        schema =
            Schema(
                type = "string",
                allowableValues =
                    [
                        "validatorTvl",
                        "totalTvl",
                        "blockProbability",
                        "delegatorTvl",
                        "nft:Strength",
                        "nft:Thunder",
                        "nft:Mjolnir",
                        "nft:VeThorX",
                        "nft:StrengthX",
                        "nft:ThunderX",
                        "nft:MjolnirX",
                        "nft:Dawn",
                        "nft:Lightning",
                        "nft:Flash",
                    ],
            ),
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getValidators(
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) validatorId: String?,
        @RequestParam(required = false) status: List<Status>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "validatorTvl") sortBy: String,
    ): PaginatedResponse<Validator> {
        val sortField = SortFieldUtils.getSortFieldValidator(sortBy)
        val pageable = toPageable(page, size, direction, sortField)

        val results =
            service.getValidators(
                validatorId = validatorId?.let { HexUtils.normalise(it) },
                endorser = endorser?.let { HexUtils.normalise(it) },
                statuses = status,
                pageable = pageable,
            )

        return paginatedResponse(results)
    }

    @GetMapping("/{validatorId}")
    @Operation(
        summary = "Get a single validator by ID",
        description = "Returns a single validator's stats by their address.",
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @CommonApiResponses
    open fun getValidatorById(@PathVariable @ValidAddress validatorId: Address): Validator {
        val normalised = HexUtils.normalise(validatorId.value)
        return service.getValidatorById(normalised)
            ?: throw ResourceNotFoundException("Validator not found for id $normalised")
    }

    @GetMapping("/delegations")
    @Operation(
        summary = "Get delegations with optional filters",
        description =
            """
            This endpoint retrieves delegation records.

            You can filter by:
            - `validator`: delegations for a specific validator
            - `tokenId`: delegations for a specific NFT tokenId
            - `statuses`: array of statuses of interest

            You can also sort and paginate.
            """,
    )
    @AddressParameter(name = "validator", description = "Filter by validator address")
    @TokenIdParameter
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "statuses",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Filter by one or more statuses",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    open fun getDelegations(
        @RequestParam(required = false) validator: String?,
        @ValidTokenId @RequestParam(required = false) tokenId: String?,
        @RequestParam(required = false) statuses: List<Status>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Delegation> {
        val pageable =
            toPageable(page, size, direction, Delegation::blockNumber.name, Delegation::id.name)

        val results: Slice<Delegation> =
            when {
                validator != null && statuses != null ->
                    delegationRepository.findByValidatorAndStatusIn(
                        HexUtils.normalise(validator),
                        statuses,
                        pageable,
                    )
                validator != null ->
                    delegationRepository.findByValidator(HexUtils.normalise(validator), pageable)
                tokenId != null -> delegationRepository.findByTokenId(tokenId, pageable)
                statuses != null -> delegationRepository.findByStatusIn(statuses, pageable)
                else -> delegationRepository.findAll(pageable)
            }

        return paginatedResponse(results)
    }

    @Deprecated(
        "Use /api/v1/validators/block-rewards and /api/v1/validators/block-rewards/{blockNumber} instead"
    )
    @GetMapping("/blocks")
    @Operation(
        summary = "Get validator block records (deprecated)",
        description =
            "Note: the original description was inaccurate. This endpoint does not return cumulative " +
                "rewards 'up to the latest block'. It returns a paginated list of individual block " +
                "reward/miss records, optionally filtered by an exact block number, validator, or status. " +
                "Deprecated: use /api/v1/validators/block-rewards for paginated listing or /api/v1/validators/block-rewards/{blockNumber} for lookup by block.",
        deprecated = true,
    )
    @BlockNumberParameter(
        description =
            "Optional block number. If provided, returns the total VTHO rewards as of this block."
    )
    @AddressParameter(name = "validator", description = "Optional validator address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "Filter by block status - either VALIDATED or MISSED.",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    open fun getValidatorBlocks(
        @RequestParam(required = false) blockNumber: Long?,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) status: BlockStatus?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorBlock> {
        val pageable = toPageable(page, size, direction, ValidatorBlock::blockNumber.name)
        return service.getValidatorBlocks(validator, status, blockNumber, pageable)
    }

    @GetMapping("/block-rewards")
    @Operation(
        summary = "Get paginated validator block reward records",
        description =
            "Returns a paginated list of validator block reward and performance records. " +
                "You can filter by validator address and/or block status (VALIDATED or MISSED). " +
                "Results are sorted by block number (default: descending).",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @BlockNumberParameter(
        `in` = ParameterIn.QUERY,
        required = false,
        description =
            "Filter results by block number. When direction is 'desc' (default), returns records at or before this block. " +
                "When direction is 'asc', returns records at or after this block.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "Filter by block status - either VALIDATED or MISSED.",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    open fun getValidatorBlockRewards(
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) blockNumber: Long?,
        @RequestParam(required = false) status: BlockStatus?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorBlock> {
        val pageable = toPageable(page, size, direction, ValidatorBlock::blockNumber.name)
        return service.getValidatorBlockRewards(validator, blockNumber, status, pageable)
    }

    @GetMapping("/block-rewards/{blockNumber}")
    @Operation(
        summary = "Get validator block records for a specific block number",
        description =
            "Returns all validator block reward records for a specific block number. " +
                "You can optionally filter by validator address to narrow to a single record.",
    )
    @BlockNumberParameter(
        `in` = ParameterIn.PATH,
        required = true,
        description = "The block number to look up.",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
    open fun getBlockByBlockNumber(
        @PathVariable blockNumber: Long,
        @ValidAddress @RequestParam(required = false) validator: Address?,
    ): List<ValidatorBlock> = service.getBlockByNumber(blockNumber, validator)

    @GetMapping("/blocks/historic/{validator}")
    @Operation(
        summary = "Get historic VTHO rewards in a custom time range",
        description =
            "Returns a time series of VTHO rewards between the given timestamps. " +
                "Granularity (hourly/daily/weekly/monthly) is automatically chosen based on the time range. " +
                "For sampled ranges, the response includes the nearest records at or before the requested boundaries. " +
                "You can filter by validator address.",
    )
    @AddressParameter(
        name = "validator",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start timestamp (inclusive)",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End timestamp (inclusive)",
        required = true,
    )
    @CommonApiResponses
    open fun getHistoricValidatorRewardsRange(
        @PathVariable @ValidAddress validator: Address,
        @RequestParam startTimestamp: Long,
        @RequestParam endTimestamp: Long,
    ): List<ValidatorBlock> =
        service.getValidatorHistoricBlocks(
            startTimestamp,
            endTimestamp,
            validator.value.lowercase(),
        )

    @GetMapping("/blocks/missed")
    @Operation(
        summary = "Get missed blocks percentage for validators",
        description =
            "Returns missed block percentages for all validators or a specific validator within a specified timeframe. " +
                "Timeframe options: DAY (last 24h), WEEK (last 7 days), MONTH (last 30 days), YEAR (last 365 days).",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "timeframe",
        schema = Schema(implementation = MissedBlocksTimeframe::class),
        description = "Time period to calculate missed blocks for",
        required = true,
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
    open fun getMissedBlocksPercentage(
        @RequestParam timeframe: MissedBlocksTimeframe,
        @ValidAddress @RequestParam(required = false) validator: Address?,
    ): AllValidatorsMissedBlocksResponse =
        service.getMissedBlocksPercentage(timeframe, validator?.value?.lowercase())

    @GetMapping("/delegations/count")
    @Operation(
        summary = "Get delegation counts by status for all validators",
        description =
            "Returns the count of delegations grouped by status (QUEUED, ACTIVE, EXITING) for all validators, " +
                "or optionally filtered to a specific validator.",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
    open fun getDelegationCounts(
        @ValidAddress @RequestParam(required = false) validator: Address?
    ): List<DelegationCountsResponse> {
        val results =
            if (validator != null) {
                delegationRepository.aggregateDelegationCountsByValidator(
                    validator.value.lowercase()
                )
            } else {
                delegationRepository.aggregateDelegationCountsByValidator()
            }

        return results.map { result ->
            val countsByStatus = result.counts.associateBy { it.status }
            DelegationCountsResponse(
                validator = result._id,
                queued = countsByStatus["QUEUED"]?.count ?: 0L,
                active = countsByStatus["ACTIVE"]?.count ?: 0L,
                exiting = countsByStatus["EXITING"]?.count ?: 0L,
            )
        }
    }
}
