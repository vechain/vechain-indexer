package org.vechain.indexer.contracts.abi

object ERC165 {
    val supportsInterface: FunctionDefinition
        get() {
            return FunctionDefinition(
              name = "supportsInterface",
              inputs =
                listOf(
                  FunctionParameter(
                    name = "interfaceId",
                    type = "bytes4",
                    components = listOf(),
                    internalType = "bytes4"
                  )
                ),
              outputs =
                listOf(
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
