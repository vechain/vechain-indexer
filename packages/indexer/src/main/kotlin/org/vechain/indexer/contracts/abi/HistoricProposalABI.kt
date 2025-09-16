package org.vechain.indexer.contracts.abi

object HistoricProposalABI {
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
                            name = "pType",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "minChecked",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "maxChecked",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "creator",
                            type = "address",
                            components = listOf(),
                            internalType = "address",
                        ),
                        FunctionParameter(
                            name = "createTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                        FunctionParameter(
                            name = "cancelTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                        FunctionParameter(
                            name = "options",
                            type = "string[]",
                            components = listOf(),
                            internalType = "string[]",
                        ),
                        FunctionParameter(
                            name = "_ratio",
                            type = "uint16[3]",
                            components = listOf(),
                            internalType = "uint16[3]",
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
                            name = "checkType",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "minChecked",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "maxChecked",
                            type = "uint8",
                            components = listOf(),
                            internalType = "uint8",
                        ),
                        FunctionParameter(
                            name = "creator",
                            type = "address",
                            components = listOf(),
                            internalType = "address",
                        ),
                        FunctionParameter(
                            name = "createTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                        FunctionParameter(
                            name = "cancelTime",
                            type = "uint64",
                            components = listOf(),
                            internalType = "uint64",
                        ),
                        FunctionParameter(
                            name = "options",
                            type = "bytes32[10]",
                            components = listOf(),
                            internalType = "bytes32[10]",
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

    val getConditionSC: FunctionDefinition
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
                            name = "checkpoint",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        ),
                        FunctionParameter(
                            name = "checkBalance",
                            type = "uint256",
                            components = listOf(),
                            internalType = "uint256",
                        ),
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
}
