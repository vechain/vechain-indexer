package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.vechain.devkit.cry.Utils
import org.vechain.indexer.abi.ERC20ABI
import org.vechain.indexer.abi.ERC721ABI
import org.vechain.indexer.abi.VIP180ABI
import org.vechain.indexer.abi.VIP181ABI
import org.vechain.indexer.model.Clause
import org.vechain.indexer.specifications.Contracts
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.ClauseUtils
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.TransactionUtils
import org.web3j.utils.Numeric
import java.math.BigInteger


@Service
class ContractService(private val thorService: ThorService) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    companion object {
        val SAMPLE_ADDRESS_1 = AddressUtil.toBigInt("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
        val SAMPLE_ADDRESS_2 = AddressUtil.toBigInt("0x435933c8064b4Ae76bE665428e0307eF2cCFBD68")
    }

    /**
     * Calls to the supportsInterface function of the ERC721 interface.
     */
    fun isErc721(contractAddress: String, rawData: String, clause: Clause): Boolean {

        try {
            val isErc721 = ContractUtils.isContractType(Contracts.ERC721, rawData) ||
                    ContractUtils.isContractType(Contracts.ERC721, clause.data)
            if (isErc721) return true

            val supportsInterface =
                ClauseUtils.contractCall(
                    contractAddress,
                    ERC721ABI.supportsInterface,
                    Utils.hexToBytes(ERC721ABI.interfaceId)
                )

            val response = thorService.executeReadOnlyCode(listOf(supportsInterface))

            val result = response.firstOrNull() ?: return false

            if (!TransactionUtils.isSuccessWithData(result)) return false

            return Numeric.toBigInt(result.data).equals(BigInteger.ONE)
        } catch (e: Exception) {
            logger.warn("Error while checking if $contractAddress is ERC721", e)
            return false
        }
    }

    /**
     * Calls to ALL the read only functions of VIP 181
     * - Can't call `ownerOf` as it will revert if the owner is the zero address
     *
     * VIP 181 does not support the supportsInterface function of the ERC165 interface.
     *
     * If it walks like a duck, and talks like a duck, then it must be a duck.
     */
    fun isVip181(contractAddress: String, rawData: String, clause: Clause): Boolean {
        try {

            val isVip181 = ContractUtils.isContractType(Contracts.VIP181, rawData) ||
                    ContractUtils.isContractType(Contracts.VIP181, clause.data)
            if (isVip181) return true

            val name = ClauseUtils.contractCall(contractAddress, VIP181ABI.name)
            val symbol = ClauseUtils.contractCall(contractAddress, VIP181ABI.symbol)
            val totalSupply = ClauseUtils.contractCall(contractAddress, VIP181ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                contractAddress,
                VIP181ABI.balanceOf,
                SAMPLE_ADDRESS_1
            )

            //Differentiator to ERC20
            val isApprovedForAll = ClauseUtils.contractCall(
                contractAddress,
                VIP181ABI.isApprovedForAll,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2
            )

            val contractCalls = listOf(
                name,
                symbol,
                totalSupply,
                balanceOf,
                isApprovedForAll
            )


            val response = thorService.executeReadOnlyCode(contractCalls)

            return response.size == contractCalls.size && response.all {
                TransactionUtils.isSuccessWithData(it)
            }
        } catch (e: Exception) {
            logger.warn("Error while checking if $contractAddress is VIP181", e)
            return false
        }
    }

    /**
     * Calls to ALL the read only functions of ERC 20
     *
     * ERC 20 does not support the supportsInterface function of the ERC165 interface.
     */
    fun isErc20(contractAddress: String, rawData: String, clause: Clause): Boolean {
        try {

            val isErc20 = ContractUtils.isContractType(Contracts.ERC20, rawData) ||
                    ContractUtils.isContractType(Contracts.ERC20, clause.data)
            if (isErc20) return true

            val totalSupply = ClauseUtils.contractCall(contractAddress, ERC20ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                contractAddress,
                ERC20ABI.balanceOf,
                SAMPLE_ADDRESS_1
            )
            val allowance = ClauseUtils.contractCall(
                contractAddress,
                ERC20ABI.allowance,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2
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
            logger.warn("Error while checking if $contractAddress is ERC20", e)
            return false
        }
    }

    /**
     * Calls to ALL the read only functions of ERC 20
     *
     * ERC 20 does not support the supportsInterface function of the ERC165 interface.
     */
    fun isVip180(contractAddress: String, rawData: String, clause: Clause): Boolean {

        try {

            val isVip180 = ContractUtils.isContractType(Contracts.VIP180, rawData) ||
                    ContractUtils.isContractType(Contracts.VIP180, clause.data)
            if (isVip180) return true

            val name = ClauseUtils.contractCall(contractAddress, VIP180ABI.name)
            val decimals = ClauseUtils.contractCall(contractAddress, VIP180ABI.decimals)
            val symbol = ClauseUtils.contractCall(contractAddress, VIP180ABI.symbol)
            val totalSupply = ClauseUtils.contractCall(contractAddress, VIP180ABI.totalSupply)
            val balanceOf = ClauseUtils.contractCall(
                contractAddress,
                VIP180ABI.balanceOf,
                SAMPLE_ADDRESS_1
            )
            val allowance = ClauseUtils.contractCall(
                contractAddress,
                VIP180ABI.allowance,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2
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
            logger.warn("Error while checking if $contractAddress is VIP180", e)
            return false
        }
    }
}