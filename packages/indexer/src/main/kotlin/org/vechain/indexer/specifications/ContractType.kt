package org.vechain.indexer.specifications

enum class ContractType(val value: String, val specification: ContractSpecification) {
    ERC20("ERC20", ERC20Contract()),
}