package org.vechain.indexer.model

data class Clause(
    override val to: String? = null,
    override val value: String? = null,
    override val data: String? = null
) : IClause