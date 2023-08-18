package org.vechain.indexer.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenRegistry(
    val name: String,
    val symbol: String,
    val decimals: Int,
    val address: String,
    val desc: String,
    val icon: String,
    val totalSupply: String,
    val website: String? = null,
    val whitePaper: String? = null,
    val links: List<SocialLink>? = null
)

@Serializable
data class SocialLink(
    val twitter: String? = null,
    val medium: String? = null,
    val github: String? = null,
    val telegram: String? = null,
    val facebook: String? = null
)
