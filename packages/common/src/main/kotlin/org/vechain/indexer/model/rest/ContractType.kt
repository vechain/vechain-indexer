package org.vechain.indexer.model.rest

enum class ContractType {
    ERC20, ERC721, VIP180, VIP181;

    companion object {
        fun byNameIgnoreCaseOrNull(name: String?): ContractType? {
            return if (name == null) null
            else values().firstOrNull { it.name.equals(name, true) }
        }
    }
}