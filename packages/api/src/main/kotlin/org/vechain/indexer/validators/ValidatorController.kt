package org.vechain.indexer.validator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VALIDATORS_PATH
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.ExceptionResponse
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.SortFieldUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validators.ValidatorService

@Profile("validator")
@Tag(name = "Validator", description = "Query validator documents")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH)
open class ValidatorController(
    private val validatorRepository: ValidatorRepository,
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
            - `validatorId`: specific validator ID
            - `status`: validator status
            - `endorser`: endorser address

            You can also sort the results by one of the supported fields and paginate.

            - `sortBy`: Choose between `validatorTvl`, `totalTvl`, or `blockProbability`
            - `page` and `size`: Controls pagination
            - `direction`: Either `asc` or `desc`
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validatorId",
        schema = Schema(type = "string"),
        description = "Filter by validator ID",
        required = false,
        example = "0x62cdf7135910dcabe336a0cdfcc3c1b16b774713",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endorser",
        schema = Schema(type = "string"),
        description = "Filter by endorser address",
        required = false,
        example = "0x06c01371bc54c59d5fd9e296c74880324b62c5fe",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = Status::class),
        description = "Filter by validator status",
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
    @PaginationParameters
    open fun getValidators(
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) validatorId: String?,
        @RequestParam(required = false) status: Status?,
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
                status = status,
                pageable = pageable,
                sortField = sortField,
                direction = direction,
            )

        return paginatedResponse(results)
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
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validator",
        schema = Schema(type = "string"),
        description = "Filter by validator address",
        required = false,
        example = "0x62cdf7135910dcabe336a0cdfcc3c1b16b774713",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenId",
        schema = Schema(type = "string"),
        description = "Filter by tokenId",
        required = false,
        example = "123456",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "statuses",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Filter by one or more statuses",
        required = false,
    )
    @PaginationParameters
    open fun getDelegations(
        @RequestParam(required = false) validator: String?,
        @RequestParam(required = false) tokenId: String?,
        @RequestParam(required = false) statuses: List<Status>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Delegation> {
        val pageable = toPageable(page, size, direction, Delegation::blockNumber.name)

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

    @GetMapping("/blocks")
    @Operation(
        summary = "Get VTHO rewards for a validator for a given block",
        description =
            "Returns the block VTHO rewards generated for the specified block number or up to the latest block. " +
                "You can optionally filter by a specific validator.",
    )
    @BlockNumberParameter(
        description =
            "Optional block number. If provided, returns the total VTHO rewards as of this block."
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validator",
        schema = Schema(type = "string"),
        description =
            "Optional validator address. If provided, returns the block VTHO rewards for this validator. ",
        required = false,
        example = "0x5e2d494fcba3e0d5773ca79f2e0a04358351a858",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "Filter by block status - either VALIDATED or MISSED.",
        required = false,
    )
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

    @GetMapping("/blocks/historic/{validator}")
    @Operation(
        summary = "Get historic VTHO rewards in a custom time range",
        description =
            "Returns a time series of VTHO rewards between the given timestamps. " +
                "Granularity (hourly/daily/weekly/monthly) is automatically chosen based on the time range. " +
                "You can filter by validator address.",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "validator",
        schema = Schema(type = "string"),
        description = "Validator address",
        required = true,
        example = "0x5e2d494fcba3e0d5773ca79f2e0a04358351a858",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "startTimestamp",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "Start timestamp (inclusive)",
        required = true,
        example = "1704067200",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endTimestamp",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "End timestamp (inclusive)",
        required = true,
        example = "1704153600",
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

    @GetMapping("/blocks/missed/{validator}")
    @Operation(
        summary = "Get percentage of missed blocks",
        description =
            "Calculates percentage of missed blocks for a validator in a block range. " +
                "startBlock must be provided. " +
                "If no endBlock is provided, endBlock defaults to best/latest block.",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "validator",
        schema = Schema(type = "string"),
        description = "Validator address",
        required = true,
        example = "0x5e2d494fcba3e0d5773ca79f2e0a04358351a858",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "startBlock",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "Start block (inclusive)",
        required = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endBlock",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "End block (inclusive) defaults to best/latest block if not provided",
        required = false,
    )
    open fun getMissedBlocksPercentage(
        @PathVariable @ValidAddress validator: Address,
        @RequestParam(required = true) startBlock: Long,
        @RequestParam(required = false) endBlock: Long?,
    ): Double = service.getMissedBlocksPercentage(validator.value.lowercase(), startBlock, endBlock)
}
