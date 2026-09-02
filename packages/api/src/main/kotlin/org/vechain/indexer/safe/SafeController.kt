package org.vechain.indexer.safe

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
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
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.TransactionId
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("safe")
@Tag(name = "Safe", description = "Safe multisig membership, proposals, and transaction state")
@Validated
@RestController
@RequestMapping(API_ROOT)
open class SafeController(private val safeService: SafeService) {

    @GetMapping("$API_VERSION/safes/owner/{address}")
    @Operation(
        summary = "List Safes for an owner",
        description =
            """
            Returns the Safes that the supplied address is — or has been — an owner of.
            Use `membership=ALL` (default) for both current and past, `CURRENT` for only active
            ownerships, and `PAST` for ownerships that have been revoked.
            """,
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address whose Safe memberships should be returned.",
    )
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getSafesForOwner(
        @ValidAddress @PathVariable address: Address,
        @RequestParam(required = false, defaultValue = "ALL") membership: SafeMembershipScope,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<SafeMembership> {
        val pageable = PaginationUtils.toPageable(page, size, direction, "addedBlock", "_id")
        return paginatedResponse(safeService.getSafesForOwner(address.value, membership, pageable))
    }

    @GetMapping("$API_VERSION/safes/{safe}/transactions")
    @Operation(
        summary = "List proposed transactions for a Safe",
        description =
            """
            Returns Safe transactions proposed via the SafeEmitter contract, paginated and sorted
            newest-first by default. Per-transaction approval / execution status is available from
            `/safes/{safe}/transactions/{txHash}/state`.
            """,
    )
    @AddressParameter(
        name = "safe",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The Safe contract address.",
    )
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.VOLATILE)
    open fun getTransactionsForSafe(
        @ValidAddress @PathVariable safe: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<SafeTxProposal> {
        val pageable = PaginationUtils.toPageable(page, size, direction, "blockNumber", "_id")
        return paginatedResponse(safeService.listProposals(safe.value, pageable))
    }

    @GetMapping("$API_VERSION/safes/{safe}/transactions/{txHash}/state")
    @Operation(
        summary = "Get Safe transaction state",
        description =
            """
            Returns the aggregated state for a single Safe transaction (approvers, executor, and
            execution status). Returns an empty document with no approvers when the Safe has not
            seen the txHash yet, so the dapp does not need to fall back to RPC for unknown hashes.
            """,
    )
    @AddressParameter(
        name = "safe",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The Safe contract address.",
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getTxState(
        @ValidAddress @PathVariable safe: Address,
        @TransactionId @PathVariable txHash: String,
    ): SafeTxState = safeService.getTxState(safe.value, txHash)
}
