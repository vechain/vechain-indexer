package org.vechain.indexer.stargate.token

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.postgres.PostgresJson

/** A per-level split as JSONB: [TokenLevel] name to the amount's decimal string. */
object TokenLevelJson {
    private val AMOUNTS = object : TypeReference<Map<String, String>>() {}

    fun write(byLevel: Map<TokenLevel, Any>): String? =
        PostgresJson.write(byLevel.mapKeys { it.key.name })

    fun read(json: String?): Map<TokenLevel, String> =
        PostgresJson.read(json, AMOUNTS).orEmpty().mapKeys { TokenLevel.valueOf(it.key) }
}
