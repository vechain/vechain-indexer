package org.vechain.indexer.contracts.abi

object AuthorityABI {
    val first: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "first",
                inputs = listOf(),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "",
                            type = "address",
                            internalType = "address",
                            components = listOf(),
                        )
                    ),
                stateMutability = "view",
            )

    val next: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "next",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "nodeMaster",
                            type = "address",
                            internalType = "address",
                            components = listOf(),
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "",
                            type = "address",
                            internalType = "address",
                            components = listOf(),
                        )
                    ),
                stateMutability = "view",
            )

    val get: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "get",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "nodeMaster",
                            type = "address",
                            internalType = "address",
                            components = listOf(),
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "listed",
                            type = "bool",
                            internalType = "bool",
                            components = listOf(),
                        ),
                        FunctionParameter(
                            name = "endorsor",
                            type = "address",
                            internalType = "address",
                            components = listOf(),
                        ),
                        FunctionParameter(
                            name = "identity",
                            type = "bytes32",
                            internalType = "bytes32",
                            components = listOf(),
                        ),
                        FunctionParameter(
                            name = "active",
                            type = "bool",
                            internalType = "bool",
                            components = listOf(),
                        ),
                    ),
                stateMutability = "view",
            )
}
