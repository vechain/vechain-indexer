package org.vechain.indexer.contracts.abi

object VIP210ABI {

    val balanceOf: FunctionDefinition
        get() {
            return FunctionDefinition(
              name = "balanceOf",
              inputs =
                listOf(
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
              outputs =
                listOf(
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
              inputs =
                listOf(
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
              outputs =
                listOf(
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

    val isApprovedForAll: FunctionDefinition = CommonABI.isApprovedForAll

    val uri: FunctionDefinition
        get() {
            return FunctionDefinition(
              name = "uri",
              inputs =
                listOf(
                  FunctionParameter(
                    name = "_id",
                    type = "uint256",
                    components = listOf(),
                    internalType = "uint256"
                  )
                ),
              outputs =
                listOf(
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
