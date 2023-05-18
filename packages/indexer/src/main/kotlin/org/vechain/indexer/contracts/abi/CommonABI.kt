package org.vechain.indexer.contracts.abi

object CommonABI {
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
                stateMutability = "view"
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
                stateMutability = "view"
            )
        }

    val decimals: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "decimals",
                inputs = listOf(),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "uint8",
                        components = listOf(),
                        internalType = "uint8"
                    )
                ),
                stateMutability = "view"
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
                stateMutability = "view"
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
                stateMutability = "view"
            )
        }

    val allowance: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "allowance",
                inputs = listOf(
                    FunctionParameter(
                        name = "owner",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    ),
                    FunctionParameter(
                        name = "spender",
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
                stateMutability = "view"
            )
        }
}