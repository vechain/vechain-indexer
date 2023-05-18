package org.vechain.indexer.contracts.abi

object VIP181ABI {

    val name: FunctionDefinition = CommonABI.name
    val symbol: FunctionDefinition = CommonABI.symbol
    val totalSupply: FunctionDefinition = CommonABI.totalSupply
    val balanceOf: FunctionDefinition = CommonABI.balanceOf

    val isApprovedForAll: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "isApprovedForAll",
                inputs = listOf(
                    FunctionParameter(
                        name = "owner",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    ),
                    FunctionParameter(
                        name = "operator",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    )
                ),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "bool",
                        components = listOf(),
                        internalType = "bool"
                    )
                ),
                stateMutability = "view"
            )
        }
}