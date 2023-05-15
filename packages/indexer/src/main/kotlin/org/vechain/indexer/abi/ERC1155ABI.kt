package org.vechain.indexer.abi

object ERC1155ABI {

    const val interfaceId = "d9b67a26"

    val supportsInterface: FunctionDefinition
        get() {
            return FunctionDefinition(
                name = "supportsInterface",
                inputs = listOf(
                    FunctionParameter(
                        name = "interfaceId",
                        type = "bytes4",
                        components = listOf(),
                        internalType = "bytes4"
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