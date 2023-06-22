package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.vechain.devkit.cry.Utils
import org.vechain.indexer.contracts.abi.*
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ArchiveRepo
import org.vechain.indexer.utils.*
import org.vechain.thor.model.Block
import org.vechain.thor.model.Clause
import org.vechain.thor.model.Transaction
import org.vechain.thor.model.TxEvent
import org.web3j.utils.Numeric
import java.math.BigInteger


@Service
class ContractService(private val thorService: ThorService, private val archiveRepo: ArchiveRepo) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    companion object {
        val SAMPLE_ADDRESS_1 = AddressUtils.toBigInt("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
        val SAMPLE_ADDRESS_2 = AddressUtils.toBigInt("0x435933c8064b4Ae76bE665428e0307eF2cCFBD68")
    }

    /**
     * Calls to the supportsInterface function of the ERC721 interface.
     */
    fun isErc721(contractAddress: String, rawData: String, clause: Clause): Boolean {

        return try {
            ContractUtils.isContractType(Contracts.ERC721, rawData) ||
                    ContractUtils.isContractType(Contracts.ERC721, clause.data) ||
                    supportsInterface(ERC721ABI.interfaceId, contractAddress)
        } catch (e: Exception) {
            logger.warn("Error while checking if $contractAddress is ERC721", e)
            false
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

    fun isErc1155(contractAddress: String, rawData: String, clause: Clause): Boolean {
        return try {
            ContractUtils.isContractType(Contracts.ERC1155, rawData) ||
                    ContractUtils.isContractType(Contracts.ERC1155, clause.data) ||
                    supportsInterface(ERC1155ABI.interfaceId, contractAddress)
        } catch (e: Exception) {
            logger.warn("Error while checking if $contractAddress is ERC1155", e)
            false
        }
    }

    fun isVip210(contractAddress: String, rawData: String, clause: Clause): Boolean {
        try {

            val isVip210 = ContractUtils.isContractType(Contracts.VIP210, rawData) ||
                    ContractUtils.isContractType(Contracts.VIP210, clause.data)

            if (isVip210) return true

            val balanceOf =
                ClauseUtils.contractCall(contractAddress, VIP210ABI.balanceOf, SAMPLE_ADDRESS_1, BigInteger.ONE)
            val balanceOfBatch = ClauseUtils.contractCall(
                contractAddress,
                VIP210ABI.balanceOfBatch,
                arrayOf(SAMPLE_ADDRESS_1, SAMPLE_ADDRESS_2),
                arrayOf(BigInteger.ONE, BigInteger.TWO)
            )
            val isApprovedForAll = ClauseUtils.contractCall(
                contractAddress,
                VIP210ABI.isApprovedForAll,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2
            )
            val uri = ClauseUtils.contractCall(contractAddress, VIP210ABI.uri, BigInteger.ONE)

            val contractCalls = listOf(
                balanceOf,
                balanceOfBatch,
                isApprovedForAll,
                uri
            )

            val response = thorService.executeReadOnlyCode(contractCalls)

            return response.size == contractCalls.size && response.all {
                TransactionUtils.isSuccessWithData(it)
            }
        } catch (e: Exception) {
            logger.warn("Error while checking if $contractAddress is VIP210", e)
            return false
        }
    }

    fun supportsInterface(interfaceId: String, contractAddress: String): Boolean {
        try {

            val supportsInterface =
                ClauseUtils.contractCall(
                    contractAddress,
                    ERC165.supportsInterface,
                    Utils.hexToBytes(interfaceId)
                )

            val response = thorService.executeReadOnlyCode(listOf(supportsInterface))

            val result = response.firstOrNull() ?: return false

            if (!TransactionUtils.isSuccessWithData(result)) return false

            return Numeric.toBigInt(result.data).equals(BigInteger.ONE)
        } catch (ex: Exception) {
            logger.warn("Error while checking if $contractAddress supportsInterface: $interfaceId", ex)
            return false
        }
    }

    fun parseContracts(
        block: Block,
        masterChangeEvents: List<Triple<TxEvent, Transaction, Clause>>,
        existingContracts: List<IndexedContract>
    ): List<IndexedContract> {
        val contracts: MutableList<IndexedContract> = mutableListOf()

        masterChangeEvents.forEach { (event, tx, clause) ->

            val contractAddress = event.address
            val master = AddressUtils.decode(event.data)

            // Handle case of two master change events for the same contract
            val multipleMasterChangeContract = contracts.find { it.address == contractAddress }
            if (multipleMasterChangeContract != null) {
                multipleMasterChangeContract.previousMasters.add(multipleMasterChangeContract.master)
                multipleMasterChangeContract.master = master
            } else {
                // If the contract is already indexed, update the master
                val contract = existingContracts.find { it.address == contractAddress }
                if (contract != null) {
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = contract.version + 1,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
                            txId = tx.id,
                            creator = contract.creator,
                            master = master,
                            rawData = contract.rawData,
                            isVip180 = contract.isVip180,
                            isVip181 = contract.isVip181,
                            isVip210 = contract.isVip210,
                            isErc20 = contract.isErc20,
                            isErc721 = contract.isErc721,
                            isErc1155 = contract.isErc1155,
                            previousMasters = contract.previousMasters.plus(contract.master).toMutableSet(),
                        )
                    )
                } else {

                    val rawData = thorService.getAccountCode(contractAddress)

                    // If the contract is not indexed yet, index it
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = 1,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
                            txId = tx.id,
                            creator = tx.origin,
                            master = master,
                            rawData = rawData,
                            isVip180 = isVip180(contractAddress, rawData, clause),
                            isVip181 = isVip181(contractAddress, rawData, clause),
                            isVip210 = isVip210(contractAddress, rawData, clause),
                            isErc20 = isErc20(contractAddress, rawData, clause),
                            isErc721 = isErc721(contractAddress, rawData, clause),
                            isErc1155 = isErc1155(contractAddress, rawData, clause),
                            previousMasters = mutableSetOf(),
                        )
                    )
                }
            }
        }
        return contracts
    }

    fun archive(contracts: List<IndexedContract>) {
        val archives = contracts.map { Archive(IdUtils.buildHashedId("${it.address}-${it.version}"), it) }
        archiveRepo.saveAll(archives)
    }

    fun getPreviousVersion(contract: IndexedContract): IndexedContract {
        val previousVersion = archiveRepo.findById(IdUtils.buildHashedId("${contract.address}-${contract.version - 1}"))
        if (!previousVersion.isPresent) throw Exception("Previous version not found")
        return previousVersion.get().data as IndexedContract
    }

}