package org.vechain.indexer.contracts.specifications

interface ContractSpecification {

    /**
     * Lists the encoded functions specified by the contract.
     *
     * The encoded functions signatures are obtained like this:
     * keccak256(canonicalName).slice(0, 4).toString('hex')
     * With canonical name being the function's signature stripped off params and whitespaces.
     */
    val functions: List<String>

    /**
     * Lists the encoded events specified by the contract.
     *
     * The encoded events signatures are obtained like this:
     * keccak256(canonicalName).toString('hex')
     * With canonical name being the event's signature stripped off params and whitespaces.
     */
    val events: List<String>
}