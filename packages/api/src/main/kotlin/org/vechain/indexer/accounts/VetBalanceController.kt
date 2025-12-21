package org.vechain.indexer.accounts

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
import org.vechain.indexer.constants.ACCOUNTS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidNonNegativeLong

@Profile("accounts")
@Tag(name = "Accounts", description = "VeChain Thor Accounts")
@Validated
@RestController
@RequestMapping(ACCOUNTS_PATH)
open class VetBalanceController(private val vetBalanceService: VetBalanceService) {
    @GetMapping("/balance/vet/{address}")
    @Operation(summary = "Retrieve VET balance history for an address")
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address to retrieve the VET balance history for.",
    )
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    open fun getVetBalanceHistory(
        @ValidAddress @PathVariable address: Address,
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): List<VetBalance> {
        TimeValidationUtils.validateTimestamps(startTimestamp, endTimestamp)

        return vetBalanceService.getByAddressInTimeRange(address, startTimestamp, endTimestamp)
    }
}
