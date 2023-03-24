package org.vechain.indexer.model

data class Clause(
    override val to: String? = null,
    override val value: String,
    override val data: String
) : IClause