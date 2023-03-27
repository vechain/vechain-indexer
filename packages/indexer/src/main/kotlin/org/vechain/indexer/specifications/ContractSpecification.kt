package org.vechain.indexer.specifications

interface ContractSpecification {
    val functions: List<String>
    val events: List<String>
}