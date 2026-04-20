package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

internal fun IndexedEvent.requireParam(name: String): String =
    params.getAsString(name) ?: error("Missing param '$name' in event: $id")

internal fun IndexedEvent.requireAddressParam(name: String): String = requireParam(name).lowercase()

internal fun IndexedEvent.requireBigDecimalParam(name: String): BigDecimal =
    requireParam(name).toBigDecimalOrNull() ?: error("Invalid decimal param '$name' in event: $id")

internal fun IndexedEvent.requireIntParam(name: String): Int =
    params.getAsInt(name) ?: error("Missing or invalid param '$name' in event: $id")

internal fun IndexedEvent.requireLongParam(name: String): Long =
    requireParam(name).toLongOrNull() ?: error("Invalid long param '$name' in event: $id")

internal fun IndexedEvent.validateRequiredParams(vararg paramNames: String) {
    paramNames.forEach { requireParam(it) }
}
