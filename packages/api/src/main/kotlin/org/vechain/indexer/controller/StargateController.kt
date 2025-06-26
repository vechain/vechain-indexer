package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.STARGATE_PATH
import org.vechain.indexer.model.Address
import org.vechain.indexer.service.StargateService
import org.vechain.indexer.validation.ValidAddress

@Profile("stargate")
@Tag(name = "Stargate", description = "Stargate related queries")
@Validated
@RestController
@RequestMapping(STARGATE_PATH)
open class StargateController(private val stargateService: StargateService) {
    @GetMapping("/total-vtho-claimed")
    @Operation(summary = "Get total VTHO claimed by Stargate users")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total VTHO claimed at a specific block number. If not provided, the latest value will be returned.",
        required = false,
        example = "12345678",
    )
    open fun getTotalVthoClaimed(@RequestParam(required = false) blockNumber: Long?): BigInteger =
        stargateService.getTotalVthoClaimed(blockNumber)

    @GetMapping("/total-vtho-claimed/{account}")
    @Operation(summary = "Get total VTHO claimed by a given account")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "The account address to query for total VTHO claimed",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
    open fun getTotalVthoClaimedByAccount(@ValidAddress @PathVariable account: String): BigInteger =
        stargateService.getTotalVthoClaimed(account)

    @GetMapping("/total-nft-holders")
    @Operation(summary = "Get total number of NFT holders in Stargate")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total number of NFT holders at a specific block number. If not provided, the latest value will be returned.",
        required = false,
        example = "12345678",
    )
    open fun getTotalNftHolders(@RequestParam(required = false) blockNumber: Long?): String {
        // TODO: Placeholder for actual implementation
        return "1000"
    }

    @GetMapping("/total-vet-staked")
    @Operation(summary = "Get total VET staked in Stargate")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total VET staked at a specific block number. If not provided, the latest value will be returned.",
        required = false,
        example = "12345678",
    )
    open fun getTotalVetStaked(@RequestParam(required = false) blockNumber: Long?): String {
        // TODO: Placeholder for actual implementation
        return "1000000000"
    }
}
