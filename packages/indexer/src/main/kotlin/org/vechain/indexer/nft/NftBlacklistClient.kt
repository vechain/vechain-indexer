package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.transaction.TransactionUtils.isSuccessWithData
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils

@Component
open class NftBlacklistClient(
    private val thorService: ThorService,
    @param:Value("\${indexer.blacklist.contract-address}") private val blacklistContract: String,
    @param:Value("\${indexer.start-block.nft-blacklist}") private val contractDeployedAt: Long,
) {
    private val functionAbi: AbiElement

    init {
        val response = AbiLoader.load(basePath = "abis/nft", names = listOf("isBlacklisted"))
        if (response.size != 1) {
            error("Failed to load ABI for 'isBlacklisted', response size: ${response.size}")
        }

        functionAbi = response.first()
    }

    /**
     * Check if an address is blacklisted at a specific block
     *
     * Returns false if the request fails. Only returns true if the address is confirmed to be
     * blacklisted.
     *
     * The reason for this is that the contract may not have been deployed at the specified block,
     *
     * @param address The address to check
     * @param block Details of the block
     */
    fun isBlacklisted(address: String, block: BlockDetails): Boolean {
        // If the contract hasn't been deployed yet at this block, return false
        if (block.blockNumber < contractDeployedAt) {
            return false
        }

        val clause = ContractUtils.createClause(blacklistContract, functionAbi, address)

        val responses = thorService.inspectClausesAtBlock(listOf(clause), block.blockId)

        if (responses.size != 1) {
            error("Unexpected number of responses: ${responses.size}")
        }

        val response = responses.first()
        if (!isSuccessWithData(response)) {
            return false
        }

        // Parse boolean from the response
        val output = FunctionReturnDecoder.decode(response.data, functionAbi.outputs)

        return output[""] as Boolean
    }
}
