package org.vechain.indexer.abi

object VIP181ABI {

    val name: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "name",
                inputs = listOf(),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "string",
                        components = listOf(),
                        internalType = "string"
                    )
                ),
                stateMutability = StateMutability.VIEW.value
            )
        }

    val symbol: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "symbol",
                inputs = listOf(),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "string",
                        components = listOf(),
                        internalType = "string"
                    )
                ),
                stateMutability = StateMutability.VIEW.value
            )
        }


    val totalSupply: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "totalSupply",
                inputs = listOf(),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "uint256",
                        components = listOf(),
                        internalType = "uint256"
                    )
                ),
                stateMutability = StateMutability.VIEW.value
            )
        }

    val balanceOf: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "balanceOf",
                inputs = listOf(
                    FunctionParameter(
                        name = "owner",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    )
                ),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "uint256",
                        components = listOf(),
                        internalType = "uint256"
                    )
                ),
                stateMutability = StateMutability.VIEW.value
            )
        }


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
                stateMutability = StateMutability.VIEW.value
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
                stateMutability = StateMutability.VIEW.value
            )
        }
}