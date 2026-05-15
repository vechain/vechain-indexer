package org.vechain.indexer.validators

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VALIDATORS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTokenId
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationStatus

@Profile("delegation")
@Tag(name = "Validator", description = "Query delegation documents")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH + "/delegations")
open class DelegationController(private val service: DelegationService) {

    @GetMapping
    @Operation(
        summary = "Get delegations with optional filters",
        description =
            """
            Returns delegations. Filterable by:
            - `validator`: delegations for a specific validator
            - `tokenId`: delegations for a specific NFT tokenId
            - `statuses`: array of statuses of interest (QUEUED / ACTIVE / EXITING / EXITED)
            """,
    )
    @AddressParameter(name = "validator", description = "Filter by validator address")
    @TokenIdParameter
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "statuses",
        schema = Schema(type = "array", implementation = DelegationStatus::class),
        description = "Filter by one or more statuses",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    open fun getDelegations(
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @ValidTokenId @RequestParam(required = false) tokenId: String?,
        @RequestParam(required = false) statuses: List<DelegationStatus>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<DelegationResponse> {
        val pageable =
            toPageable(page, size, direction, Delegation::blockNumber.name, Delegation::id.name)
        val results = service.getDelegations(validator?.value, tokenId, statuses, pageable)
        return paginatedResponse(results.map(DelegationResponse::from))
    }

    @GetMapping("/count")
    @Operation(
        summary = "Get delegation counts by status for all validators",
        description =
            "Returns the count of delegations grouped by status (QUEUED, ACTIVE, EXITING) " +
                "for all validators, or optionally filtered to a specific validator.",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
    open fun getDelegationCounts(
        @ValidAddress @RequestParam(required = false) validator: Address?
    ): List<DelegationCountsResponse> = service.getDelegationCounts(validator)
}
