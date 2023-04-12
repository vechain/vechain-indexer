package org.vechain.indexer.abi

enum class StateMutability(val value: String) {
    PURE("pure"),
    VIEW("view"),
    NON_PAYABLE("nonpayable"),
    PAYABLE("payable"),
    CONSTANT("constant");
}