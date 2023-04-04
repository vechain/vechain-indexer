package org.vechain.indexer.validation

import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.utils.AddressUtil

object Validation {

    /**
     * Check if the address is valid.
     *
     * @throws BadRequestException if the address is invalid.
     */
    fun checkAddress(address: String) {
        if (AddressUtil.isNotValid(address))
            throw BadRequestException("Invalid address: $address")
    }

    /**
     * Check if the addresses are valid.
     *
     * @throws BadRequestException if any address is invalid.
     */
    fun checkAddresses(addresses: List<String>) {
        addresses.forEach { checkAddress(it) }
    }
}