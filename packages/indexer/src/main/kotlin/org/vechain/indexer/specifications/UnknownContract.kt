package org.vechain.indexer.specifications

data class UnknownContract(
    override val functions: List<String> = listOf(),
    override val events: List<String> = listOf()
) : ContractSpecification