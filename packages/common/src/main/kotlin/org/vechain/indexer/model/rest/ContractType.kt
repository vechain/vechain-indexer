package org.vechain.indexer.model.rest

enum class ContractType {
    VIP180, VIP181, VIP210, ERC20, ERC721, ERC1155;

    companion object {
        fun byNameIgnoreCaseOrNull(name: String?): ContractType? {
            return if (name == null) null
            else values().firstOrNull { it.name.equals(name, true) }
        }
    }
}