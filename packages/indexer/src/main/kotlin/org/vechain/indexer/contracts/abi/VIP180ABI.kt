package org.vechain.indexer.contracts.abi

object VIP180ABI {

    val totalSupply: FunctionDefinition = CommonABI.totalSupply
    val balanceOf: FunctionDefinition = CommonABI.balanceOf
    val allowance = CommonABI.allowance

    val name = CommonABI.name
    val symbol = CommonABI.symbol
    val decimals = CommonABI.decimals
}
