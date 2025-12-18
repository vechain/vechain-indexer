package org.vechain.indexer.contracts

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress

@Profile("contracts")
@Tag(name = "Contracts", description = "Information about smart contracts")
@Validated
@RestController
@RequestMapping(CONTRACTS_PATH)
open class ContractsController(private val contractsService: ContractsService) {
    @GetMapping("/{address}")
    @Operation(summary = "Retrieve a contract by address")
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address of the contract to retrieve.",
    )
    @CommonApiResponses
    open fun getContract(@ValidAddress @PathVariable address: Address): Contract =
        contractsService.getByAddress(address)
            ?: throw ResourceNotFoundException("Contract not found for address $address")
}
