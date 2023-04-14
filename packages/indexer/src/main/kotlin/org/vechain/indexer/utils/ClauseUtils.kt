package org.vechain.indexer.utils

import org.vechain.devkit.Function
import org.vechain.indexer.abi.FunctionDefinition
import org.vechain.indexer.model.Clause

object ClauseUtils {
    fun contractCall(address: String, function: FunctionDefinition, vararg args: Any): Clause {
        val func = Function(JsonUtils.mapper.writeValueAsString(function))
        val encoded = func.encodeToHex(true, *args)
        return Clause(to = address, data = encoded, value = "0x0")
    }
}