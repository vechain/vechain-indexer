package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.devkit.cry.Utils
import org.vechain.indexer.contracts.abi.*
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.utils.AddressUtils
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.TransactionUtils
import org.web3j.utils.Numeric

@Profile("contracts")
@Service
open class ContractService(
    private val contractRepository: ContractRepository,
    private val contractArchiveService: ArchiveService<IndexedContract, ContractArchive>,
    private val thorService: ThorService,
) {
    companion object {
        val SAMPLE_ADDRESS_1 = AddressUtils.toBigInt("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
        val SAMPLE_ADDRESS_2 = AddressUtils.toBigInt("0x435933c8064b4Ae76bE665428e0307eF2cCFBD68")
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun update(
        updated: List<IndexedContract>,
        existing: List<IndexedContract>,
    ) {
        if (updated.isNotEmpty()) {
            contractRepository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            contractArchiveService.saveAll(existing)
        }
    }

    open fun getExisting(contractAddresses: List<String>): List<IndexedContract> =
        contractRepository.findAllById(contractAddresses).toList()

    /** Calls to the supportsInterface function of the ERC721 interface. */
    open fun isErc721(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean =
        ContractUtils.isContractType(Contracts.ERC721, rawData) ||
            ContractUtils.isContractType(Contracts.ERC721, clauseData) ||
            supportsInterface(ERC721ABI.interfaceId, contractAddress)

    /**
     * Calls to ALL the read only functions of VIP 181
     * - Can't call `ownerOf` as it will revert if the owner is the zero address
     *
     * VIP 181 does not support the supportsInterface function of the ERC165 interface.
     *
     * If it walks like a duck, and talks like a duck, then it must be a duck.
     */
    open fun isVip181(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean {
        val isVip181 =
            ContractUtils.isContractType(Contracts.VIP181, rawData) ||
                ContractUtils.isContractType(Contracts.VIP181, clauseData)
        if (isVip181) return true

        val name = ContractUtils.createClause(contractAddress, VIP181ABI.name)
        val symbol = ContractUtils.createClause(contractAddress, VIP181ABI.symbol)
        val totalSupply = ContractUtils.createClause(contractAddress, VIP181ABI.totalSupply)
        val balanceOf =
            ContractUtils.createClause(contractAddress, VIP181ABI.balanceOf, SAMPLE_ADDRESS_1)

        // Differentiator to ERC20
        val isApprovedForAll =
            ContractUtils.createClause(
                contractAddress,
                VIP181ABI.isApprovedForAll,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2,
            )

        val contractCalls = listOf(name, symbol, totalSupply, balanceOf, isApprovedForAll)

        val response = thorService.executeReadOnlyCode(contractCalls)

        return response.size == contractCalls.size &&
            response.all { TransactionUtils.isSuccessWithData(it) }
    }

    /**
     * Calls to ALL the read only functions of ERC 20
     *
     * ERC 20 does not support the supportsInterface function of the ERC165 interface.
     */
    open fun isErc20(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean {
        val isErc20 =
            ContractUtils.isContractType(Contracts.ERC20, rawData) ||
                ContractUtils.isContractType(Contracts.ERC20, clauseData)
        if (isErc20) return true

        val totalSupply = ContractUtils.createClause(contractAddress, ERC20ABI.totalSupply)
        val balanceOf =
            ContractUtils.createClause(contractAddress, ERC20ABI.balanceOf, SAMPLE_ADDRESS_1)
        val allowance =
            ContractUtils.createClause(
                contractAddress,
                ERC20ABI.allowance,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2,
            )

        val contractCalls = listOf(totalSupply, balanceOf, allowance)

        val response = thorService.executeReadOnlyCode(contractCalls)

        return response.size == contractCalls.size &&
            response.all { TransactionUtils.isSuccessWithData(it) }
    }

    /**
     * Calls to ALL the read only functions of ERC 20
     *
     * ERC 20 does not support the supportsInterface function of the ERC165 interface.
     */
    open fun isVip180(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean {
        val isVip180 =
            ContractUtils.isContractType(Contracts.VIP180, rawData) ||
                ContractUtils.isContractType(Contracts.VIP180, clauseData)
        if (isVip180) return true

        val name = ContractUtils.createClause(contractAddress, VIP180ABI.name)
        val decimals = ContractUtils.createClause(contractAddress, VIP180ABI.decimals)
        val symbol = ContractUtils.createClause(contractAddress, VIP180ABI.symbol)
        val totalSupply = ContractUtils.createClause(contractAddress, VIP180ABI.totalSupply)
        val balanceOf =
            ContractUtils.createClause(contractAddress, VIP180ABI.balanceOf, SAMPLE_ADDRESS_1)
        val allowance =
            ContractUtils.createClause(
                contractAddress,
                VIP180ABI.allowance,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2,
            )

        val contractCalls = listOf(name, symbol, decimals, totalSupply, balanceOf, allowance)

        val response = thorService.executeReadOnlyCode(contractCalls)

        return response.size == contractCalls.size &&
            response.all { TransactionUtils.isSuccessWithData(it) }
    }

    open fun isErc1155(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean =
        ContractUtils.isContractType(Contracts.ERC1155, rawData) ||
            ContractUtils.isContractType(Contracts.ERC1155, clauseData) ||
            supportsInterface(ERC1155ABI.interfaceId, contractAddress)

    open fun isVip210(
        contractAddress: String,
        rawData: String,
        clauseData: String,
    ): Boolean {
        val isVip210 =
            ContractUtils.isContractType(Contracts.VIP210, rawData) ||
                ContractUtils.isContractType(Contracts.VIP210, clauseData)

        if (isVip210) return true

        val balanceOf =
            ContractUtils.createClause(
                contractAddress,
                VIP210ABI.balanceOf,
                SAMPLE_ADDRESS_1,
                BigInteger.ONE,
            )
        val balanceOfBatch =
            ContractUtils.createClause(
                contractAddress,
                VIP210ABI.balanceOfBatch,
                arrayOf(SAMPLE_ADDRESS_1, SAMPLE_ADDRESS_2),
                arrayOf(BigInteger.ONE, BigInteger.TWO),
            )
        val isApprovedForAll =
            ContractUtils.createClause(
                contractAddress,
                VIP210ABI.isApprovedForAll,
                SAMPLE_ADDRESS_1,
                SAMPLE_ADDRESS_2,
            )
        val uri = ContractUtils.createClause(contractAddress, VIP210ABI.uri, BigInteger.ONE)

        val contractCalls = listOf(balanceOf, balanceOfBatch, isApprovedForAll, uri)

        val response = thorService.executeReadOnlyCode(contractCalls)

        return response.size == contractCalls.size &&
            response.all { TransactionUtils.isSuccessWithData(it) }
    }

    open fun supportsInterface(
        interfaceId: String,
        contractAddress: String,
    ): Boolean {
        val supportsInterface =
            ContractUtils.createClause(
                contractAddress,
                ERC165.supportsInterface,
                Utils.hexToBytes(interfaceId),
            )

        val response = thorService.executeReadOnlyCode(listOf(supportsInterface))

        val result = response.firstOrNull() ?: return false

        if (!TransactionUtils.isSuccessWithData(result)) return false

        return Numeric.toBigInt(result.data).equals(BigInteger.ONE)
    }

    open fun parseContracts(
        masterChangeEvents: List<IndexedEvent>,
        existingContracts: List<IndexedContract>,
    ): List<IndexedContract> {
        val contracts: MutableList<IndexedContract> = mutableListOf()

        masterChangeEvents.forEach { event ->
            val contractAddress = event.address ?: return@forEach
            val master = event.params.getAsString("newMaster") ?: return@forEach

            // Handle case of two master change events for the same contract
            val multipleMasterChangeContract = contracts.find { it.address == contractAddress }
            if (multipleMasterChangeContract != null) {
                multipleMasterChangeContract.previousMasters.add(
                    multipleMasterChangeContract.master,
                )
                multipleMasterChangeContract.master = master
            } else {
                // If the contract is already indexed, update the master
                val contract = existingContracts.find { it.address == contractAddress }
                if (contract != null) {
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = contract.version + 1,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                            txId = event.txId,
                            creator = contract.creator,
                            master = master,
                            rawData = contract.rawData,
                            isVip180 = contract.isVip180,
                            isVip181 = contract.isVip181,
                            isVip210 = contract.isVip210,
                            isErc20 = contract.isErc20,
                            isErc721 = contract.isErc721,
                            isErc1155 = contract.isErc1155,
                            previousMasters =
                                contract.previousMasters.plus(contract.master).toMutableSet(),
                        ),
                    )
                } else {
                    val rawData = thorService.getAccountCode(contractAddress)
                    // If the contract is not indexed yet, index it
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = 1,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                            txId = event.txId,
                            creator = event.origin as String,
                            master = master,
                            rawData = rawData,
                            isVip180 = isVip180(contractAddress, rawData, event.raw!!.data),
                            isVip181 = isVip181(contractAddress, rawData, event.raw!!.data),
                            isVip210 = isVip210(contractAddress, rawData, event.raw!!.data),
                            isErc20 = isErc20(contractAddress, rawData, event.raw!!.data),
                            isErc721 = isErc721(contractAddress, rawData, event.raw!!.data),
                            isErc1155 = isErc1155(contractAddress, rawData, event.raw!!.data),
                            previousMasters = mutableSetOf(),
                        ),
                    )
                }
            }
        }
        return contracts
    }
}
