package org.vechain.indexer.contracts.abi

object ERC20ABI {
    val totalSupply: FunctionDefinition = CommonABI.totalSupply
    val balanceOf: FunctionDefinition = CommonABI.balanceOf
    val allowance = CommonABI.allowance
}
