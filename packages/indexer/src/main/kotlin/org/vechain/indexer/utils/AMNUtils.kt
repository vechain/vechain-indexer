package org.vechain.indexer.utils

import org.vechain.indexer.contracts.abi.AuthorityABI
import org.vechain.indexer.thor.model.Clause

object AMNUtils {
    fun createFirstClause(contractAddress: String): Clause =
        ContractUtils.createClause(contractAddress, AuthorityABI.first)

    fun createNextClause(contractAddress: String, nodeMaster: String): Clause =
        ContractUtils.createClause(
            contractAddress,
            AuthorityABI.next,
            AddressUtils.toBigInt(nodeMaster),
        )

    fun createGetClause(contractAddress: String, nodeMaster: String): Clause =
        ContractUtils.createClause(
            contractAddress,
            AuthorityABI.get,
            AddressUtils.toBigInt(nodeMaster),
        )

    const val ACTION_ADDED = "0x6164646564000000000000000000000000000000000000000000000000000000"
    const val ACTION_REVOKED = "0x7265766f6b656400000000000000000000000000000000000000000000000000"
}
