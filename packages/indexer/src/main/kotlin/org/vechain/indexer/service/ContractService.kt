package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.vechain.devkit.cry.Utils
import org.vechain.indexer.abi.ERC20ABI
import org.vechain.indexer.abi.ERC721ABI
import org.vechain.indexer.abi.VIP180ABI
import org.vechain.indexer.abi.VIP181ABI
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.ClauseUtils
import org.vechain.indexer.utils.TransactionUtils
import org.web3j.utils.Numeric
import java.math.BigInteger

@Service
class ContractService(private val thorService: ThorService) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    /**
     * Calls to the supportsInterface function of the ERC721 interface.
     */
    fun isErc721(address: String): Boolean {

        try {
            val supportsInterface =
                ClauseUtils.contractCall(address, ERC721ABI.supportsInterface, Utils.hexToBytes(ERC721ABI.interfaceId))

            val response = thorService.executeReadOnlyCode(listOf(supportsInterface))

            val result = response.firstOrNull() ?: return false

            if (!TransactionUtils.isSuccessWithData(result)) return false

            return Numeric.toBigInt(result.data).equals(BigInteger.ONE)
        } catch (e: Exception) {
            logger.warn("Error while checking if $address is ERC721", e)
            return false
        }
    }

    /**
     * Calls to ALL the read only functions of VIP 181
     *
     * If it walks like a duck, and talks like a duck, then it must be a duck.
     */
    fun isVip181(address: String): Boolean {
        try {
            val name = ClauseUtils.contractCall(address, VIP181ABI.name)
            val symbol = ClauseUtils.contractCall(address, VIP181ABI.symbol)
            val totalSupply = ClauseUtils.contractCall(address, VIP181ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                address,
                VIP181ABI.balanceOf,
                AddressUtil.toBigInt(address)
            )

            //Differentiator to ERC20
            val ownerOf = ClauseUtils.contractCall(address, VIP181ABI.ownerOf, BigInteger.ZERO)
            //Differentiator to ERC20
            val isApprovedForAll = ClauseUtils.contractCall(
                address,
                VIP181ABI.isApprovedForAll,
                AddressUtil.toBigInt(address),
                AddressUtil.toBigInt(address)
            )

            val contractCalls = listOf(
                name,
                symbol,
                totalSupply,
                balanceOf,
                ownerOf,
                isApprovedForAll
            )

            val response = thorService.executeReadOnlyCode(contractCalls)

            return response.size == contractCalls.size && response.all {
                TransactionUtils.isSuccessWithData(it)
            }
        } catch (e: Exception) {
            logger.warn("Error while checking if $address is VIP181", e)
            return false
        }
    }

    fun isErc20(address: String): Boolean {
        try {
            val totalSupply = ClauseUtils.contractCall(address, ERC20ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                address,
                ERC20ABI.balanceOf,
                AddressUtil.toBigInt(address)
            )
            val allowance = ClauseUtils.contractCall(
                address,
                ERC20ABI.allowance,
                AddressUtil.toBigInt(address),
                AddressUtil.toBigInt(address)
            )

            val contractCalls = listOf(
                totalSupply,
                balanceOf,
                allowance
            )

            val response = thorService.executeReadOnlyCode(contractCalls)

            return response.size == contractCalls.size && response.all {
                TransactionUtils.isSuccessWithData(it)
            }
        } catch (e: Exception) {
            logger.warn("Error while checking if $address is ERC20", e)
            return false
        }
    }

    fun isVip180(address: String): Boolean {
        try {
            val name = ClauseUtils.contractCall(address, VIP180ABI.name)
            val decimals = ClauseUtils.contractCall(address, VIP180ABI.decimals)
            val symbol = ClauseUtils.contractCall(address, VIP180ABI.symbol)
            val totalSupply = ClauseUtils.contractCall(address, VIP180ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                address,
                VIP180ABI.balanceOf,
                AddressUtil.toBigInt(address)
            )
            val allowance = ClauseUtils.contractCall(
                address,
                VIP180ABI.allowance,
                AddressUtil.toBigInt(address),
                AddressUtil.toBigInt(address)
            )

            val contractCalls = listOf(
                name,
                symbol,
                decimals,
                totalSupply,
                balanceOf,
                allowance
            )

            val response = thorService.executeReadOnlyCode(contractCalls)

            return response.size == contractCalls.size && response.all {
                TransactionUtils.isSuccessWithData(it)
            }

        } catch (e: Exception) {
            logger.warn("Error while checking if $address is VIP180", e)
            return false
        }
    }
}