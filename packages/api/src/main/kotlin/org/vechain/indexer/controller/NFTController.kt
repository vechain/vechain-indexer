package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.exception.InternalServerException
import org.vechain.indexer.model.NFT
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.validation.Address


@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping("api/v1/nfts")
open class NFTController {

    @GetMapping("{address}")
    @Operation(summary = "Get all NFTs owned by an address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the NFT owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getOwnedNFTs(
        @Address @PathVariable(required = true) address: String
    ): Array<NFT> {
        throw InternalServerException("This endpoint is not yet implemented")
    }
}