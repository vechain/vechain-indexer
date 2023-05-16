package org.vechain.indexer.abi

object VIP210ABI {

    val balanceOf: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "balanceOf",
                inputs = listOf(
                    FunctionParameter(
                        name = "_owner",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    ),
                    FunctionParameter(
                        name = "_id",
                        type = "uint256",
                        components = listOf(),
                        internalType = "uint256"
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

    val balanceOfBatch: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "balanceOfBatch",
                inputs = listOf(
                    FunctionParameter(
                        name = "_owners",
                        type = "address[]",
                        components = listOf(),
                        internalType = "address[]"
                    ),
                    FunctionParameter(
                        name = "_ids",
                        type = "uint256[]",
                        components = listOf(),
                        internalType = "uint256[]"
                    )
                ),
                outputs = listOf(
                    FunctionParameter(
                        name = "",
                        type = "uint256[]",
                        components = listOf(),
                        internalType = "uint256[]"
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
                        name = "_owner",
                        type = "address",
                        components = listOf(),
                        internalType = "address"
                    ),
                    FunctionParameter(
                        name = "_operator",
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

    val uri: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "uri",
                inputs = listOf(
                    FunctionParameter(
                        name = "_id",
                        type = "uint256",
                        components = listOf(),
                        internalType = "uint256"
                    )
                ),
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
}