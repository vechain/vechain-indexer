package org.vechain.indexer.contracts.abi

object HistoricalProposalABI {
    // All Stakeholders contract (dynamic arrays)
    val getBasicInfo: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "getBasicInfo",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "_proposalId",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "title",
                            type = "string",
                            components = listOf(),
                            internalType = "string",
                        ),
                        FunctionParameter(
                            name = "options",
                            type = "string[]",
                            components = listOf(),
                            internalType = "string[]",
                        ),
                        FunctionParameter(
                            name = "createTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                    ),
                stateMutability = "view",
            )

    val getCondition: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "getCondition",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "_proposalId",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "votingStartTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                        FunctionParameter(
                            name = "votingEndTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                    ),
                stateMutability = "view",
            )

    val getTally: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "getTally",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "_proposalId",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "tally",
                            type = "uint64[]",
                            components = listOf(),
                            internalType = "uint64[]",
                        )
                    ),
                stateMutability = "view",
            )

    // Steering Committee contract (fixed arrays)
    val getBasicInfoSC: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "getBasicInfo",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "_proposalId",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "title",
                            type = "string",
                            components = listOf(),
                            internalType = "string",
                        ),
                        FunctionParameter(
                            name = "options",
                            type = "bytes32[10]",
                            components = listOf(),
                            internalType = "bytes32[10]",
                        ),
                        FunctionParameter(
                            name = "createTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                    ),
                stateMutability = "view",
            )

    val getTallySC: FunctionDefinition
        get() =
            FunctionDefinition(
                name = "getTally",
                inputs =
                    listOf(
                        FunctionParameter(
                            name = "_proposalId",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        )
                    ),
                outputs =
                    listOf(
                        FunctionParameter(
                            name = "tally",
                            type = "uint64[10]",
                            components = listOf(),
                            internalType = "uint64[10]",
                        )
                    ),
                stateMutability = "view",
            )
}
