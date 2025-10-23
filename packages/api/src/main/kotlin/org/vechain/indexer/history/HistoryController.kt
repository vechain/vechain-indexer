package org.vechain.indexer.history

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.API_ROOT
import org.vechain.indexer.constants.API_VERSION
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ArrayValidationUtils
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@RequestMapping(API_ROOT)
@Profile("history")
@Tag(name = "History", description = "Query on-chain event history")
@Validated
@RestController
open class HistoryController(private val historyService: HistoryService) {
    // TODO: Remove this when veworld have migrated to V2 History API
    @GetMapping("$API_VERSION/history/{account}")
    @Operation(summary = "Get account history - This will be deprecated post hayabusa release")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "A valid account address",
        required = true,
        example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "searchBy",
        array =
            ArraySchema(
                schema =
                    Schema(
                        type = "string",
                        allowableValues = ["to", "from", "origin", "gasPayer"],
                        description =
                            "Fields to search by. Defaults to ['to', 'from', 'origin'] if not provided.",
                    )
            ),
        description = "Array of fields to search by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "eventName",
        array =
            ArraySchema(
                schema =
                    Schema(
                        type = "string",
                        allowableValues =
                            [
                                "B3TR_SWAP_VOT3_TO_B3TR",
                                "B3TR_SWAP_B3TR_TO_VOT3",
                                "B3TR_PROPOSAL_SUPPORT",
                                "B3TR_CLAIM_REWARD",
                                "B3TR_UPGRADE_GM",
                                "B3TR_ACTION",
                                "B3TR_PROPOSAL_VOTE",
                                "B3TR_XALLOCATION_VOTE",
                                "TRANSFER_VET",
                                "TRANSFER_FT",
                                "TRANSFER_NFT",
                                "TRANSFER_SF",
                                "SWAP_VET_TO_FT",
                                "SWAP_FT_TO_VET",
                                "SWAP_FT_TO_FT",
                                "UNKNOWN_TX",
                                "NFT_SALE",
                                "STARGATE_DELEGATE",
                                "STARGATE_CLAIM_REWARDS_BASE",
                                "STARGATE_CLAIM_REWARDS_DELEGATE",
                                "STARGATE_UNDELEGATE",
                                "STARGATE_STAKE",
                                "STARGATE_UNSTAKE",
                                "STARGATE_DELEGATE_ACTIVE",
                                "STARGATE_DELEGATE_REQUEST",
                                "STARGATE_DELEGATE_EXIT_REQUEST",
                                "STARGATE_DELEGATION_EXITED_VALIDATOR",
                                "STARGATE_DELEGATION_EXITED",
                                "STARGATE_CLAIM_REWARDS",
                                "STARGATE_BOOST",
                                "STARGATE_MANAGER_ADDED",
                                "STARGATE_MANAGER_REMOVED",
                                "VEVOTE_VOTE_CAST",
                            ],
                    )
            ),
        description = "Filter by specific transaction names.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The contract address",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description =
            "Return transactions after and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description =
            "Return transactions before and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getUsersHistory(
        @ValidAddress @PathVariable account: Address,
        @RequestParam(required = false) eventName: List<String>?,
        @RequestParam(required = false) searchBy: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<HistoryEventDto> {
        val mappedInputs: List<String>? = HistoryUtils.mapInputToNew(eventName)

        // Validate query parameters
        val validatedEventNames: List<String> =
            ArrayValidationUtils.validateArray(
                input = mappedInputs,
                allowedValues = HistoryEventName.entries.map { it.name }.toSet(),
                fieldName = "eventName",
            )

        val validatedSearchFields =
            ArrayValidationUtils.validateArray(
                input = searchBy,
                allowedValues = setOf("to", "from", "origin", "gasPayer"),
                fieldName = "searchBy",
            )

        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size?.plus(1),
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        val slice =
            historyService.findUserHistoryByFilters(
                account = account.value,
                eventNames = validatedEventNames, // query on new/canonical names
                searchFields = validatedSearchFields,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )

        // Convert Slice<IndexedHistoryEvent> -> Slice<HistoryEventDto> with legacy names
        val dtoSlice = slice.map { HistoryEventDto.fromIndexed(it, legacy = true) }

        return paginatedResponse(dtoSlice)
    }

    @GetMapping("/v2/history/{account}")
    @Operation(summary = "Get account history")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "A valid account address",
        required = true,
        example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "searchBy",
        array =
            ArraySchema(
                schema =
                    Schema(
                        type = "string",
                        allowableValues = ["to", "from", "origin", "gasPayer"],
                        description =
                            "Fields to search by. Defaults to ['to', 'from', 'origin'] if not provided.",
                    )
            ),
        description = "Array of fields to search by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "eventName",
        array = ArraySchema(schema = Schema(implementation = HistoryEventName::class)),
        description = "Filter by specific transaction names.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The contract address",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description =
            "Return transactions after and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description =
            "Return transactions before and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getUsersHistoryV2(
        @ValidAddress @PathVariable account: Address,
        @RequestParam(required = false) eventName: List<String>?,
        @RequestParam(required = false) searchBy: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        // Validate query parameters
        val validatedEventNames =
            ArrayValidationUtils.validateArray(
                input = eventName,
                allowedValues = HistoryEventName.entries.map { it.name }.toSet(),
                fieldName = "eventName",
            )

        val validatedSearchFields =
            ArrayValidationUtils.validateArray(
                input = searchBy,
                allowedValues = setOf("to", "from", "origin", "gasPayer"),
                fieldName = "searchBy",
            )

        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size?.plus(1),
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            historyService.findUserHistoryByFilters(
                account = account.value,
                eventNames = validatedEventNames,
                searchFields = validatedSearchFields,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }

    @GetMapping("$API_VERSION/history/token/{tokenId}")
    @Operation(summary = "Get token history")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "tokenId",
        schema = Schema(type = "string"),
        description = "A valid account tokenId",
        required = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "eventName",
        array =
            ArraySchema(
                schema =
                    Schema(
                        type = "string",
                        allowableValues =
                            [
                                "STARGATE_DELEGATE_LEGACY",
                                "STARGATE_STAKE",
                                "STARGATE_DELEGATE_REQUEST",
                                "STARGATE_DELEGATE_ACTIVE",
                                "STARGATE_UNDELEGATE_LEGACY",
                                "STARGATE_DELEGATE_EXIT_REQUEST",
                                "STARGATE_DELEGATION_EXITED_VALIDATOR",
                                "STARGATE_DELEGATION_EXITED",
                                "STARGATE_CLAIM_REWARDS",
                                "STARGATE_BOOST",
                                "STARGATE_MANAGER_ADDED",
                                "STARGATE_MANAGER_REMOVED",
                                "VEVOTE_VOTE_CAST",
                                "NFT_SALE",
                                "TRANSFER_NFT",
                                "B3TR_UPGRADE_GM",
                            ],
                    )
            ),
        description = "Filter by specific transaction names.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The contract address",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description =
            "Return transactions after and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description =
            "Return transactions before and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTokenHistory(
        @PathVariable(required = true) tokenId: String,
        @RequestParam(required = false) eventName: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        // Validate query parameters
        val validatedEventNames =
            ArrayValidationUtils.validateArray(
                input = eventName,
                allowedValues = allowedTokenNames,
                fieldName = "eventName",
            )

        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size?.plus(1),
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            historyService.findTokenIdHistoryByFilters(
                tokenId = tokenId,
                eventNames = validatedEventNames,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }

    private val allowedTokenNames: Set<String> =
        setOf(
            "STARGATE_DELEGATE_LEGACY",
            "STARGATE_STAKE",
            "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
            "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
            "STARGATE_UNSTAKE",
            "STARGATE_DELEGATE_REQUEST",
            "STARGATE_DELEGATE_ACTIVE",
            "STARGATE_UNDELEGATE_LEGACY",
            "STARGATE_DELEGATE_EXIT_REQUEST",
            "STARGATE_DELEGATION_EXITED_VALIDATOR",
            "STARGATE_DELEGATION_EXITED",
            "STARGATE_CLAIM_REWARDS",
            "STARGATE_BOOST",
            "STARGATE_MANAGER_ADDED",
            "STARGATE_MANAGER_REMOVED",
            "VEVOTE_VOTE_CAST",
            "NFT_SALE",
            "TRANSFER_NFT",
            "B3TR_UPGRADE_GM",
        )
}
