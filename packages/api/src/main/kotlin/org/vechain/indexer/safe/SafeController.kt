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
@Tag(name = "Safe", description = "Safe multisig wallets: owners, proposals and approvals.")
@Validated
@RestController
@RequestMapping(API_ROOT)
open class SafeController(private val safeService: SafeService) {

    @GetMapping("$API_VERSION/safes/owner/{address}")
    @Operation(
        summary = "List Safes for an owner",
        description =
            "Safes the address owns or used to own. `membership` narrows this: `ALL` " +
                "(default) for both, `CURRENT` for Safes it owns now, `PAST` for Safes it was " +
                "removed from.",
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "Owner address.",
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
        val pageable = PaginationUtils.toPageable(page, size, direction, "addedBlock", "safe")
        return paginatedResponse(safeService.getSafesForOwner(address.value, membership, pageable))
    }

    @GetMapping("$API_VERSION/safes/{safe}/transactions")
    @Operation(
        summary = "List proposed transactions for a Safe",
        description =
            "Transactions proposed for this Safe, newest first. For a transaction's approvals" +
                " and whether it ran, use `/safes/{safe}/transactions/{txHash}/state`.",
    )
    @AddressParameter(
        name = "safe",
        `in` = ParameterIn.PATH,
        required = true,
        description = "Safe address.",
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
        val pageable = PaginationUtils.toPageable(page, size, direction, "blockNumber", "txHash")
        return paginatedResponse(safeService.listProposals(safe.value, pageable))
    }

    @GetMapping("$API_VERSION/safes/{safe}/transactions/{txHash}/state")
    @Operation(
        summary = "Get Safe transaction state",
        description =
            "Who approved a Safe transaction, who executed it, and whether it ran. A `txHash`" +
                " the Safe hasn't seen returns an empty result with no approvers, not an error.",
    )
    @AddressParameter(
        name = "safe",
        `in` = ParameterIn.PATH,
        required = true,
        description = "Safe address.",
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getTxState(
        @ValidAddress @PathVariable safe: Address,
        @TransactionId @PathVariable txHash: String,
    ): SafeTxState = safeService.getTxState(safe.value, txHash)
}
