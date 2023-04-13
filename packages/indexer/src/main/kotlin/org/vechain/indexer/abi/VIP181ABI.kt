package org.vechain.indexer.abi

object VIP181ABI {

    val name: FunctionDefinition = CommonABI.name
    val symbol: FunctionDefinition = CommonABI.symbol
    val totalSupply: FunctionDefinition = CommonABI.totalSupply
    val balanceOf: FunctionDefinition = CommonABI.balanceOf

    val ownerOf: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "ownerOf",
                inputs = listOf(
                    FunctionParameter(
                        name = "tokenId",
                        type = "uint256",
                        components = listOf(),
                        internalType = "uint256"
                    )
                ),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    )
                ),
                stateMutability = "view"
            )
        }

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