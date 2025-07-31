package org.vechain.indexer

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils

@Component
class StringToAddressConverter : Converter<String, Address> {

    override fun convert(source: String): Address? {
        return if (source.isNullOrEmpty()) null else Address(HexUtils.normalise(source))
    }
}
