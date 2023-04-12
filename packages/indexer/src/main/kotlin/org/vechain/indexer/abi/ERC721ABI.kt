package org.vechain.indexer.abi

object ERC721ABI {

    const val interfaceId = "80ac58cd"

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
                stateMutability = StateMutability.VIEW.value
            )
        }
}