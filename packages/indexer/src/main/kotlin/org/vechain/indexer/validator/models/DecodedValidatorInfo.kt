package org.vechain.indexer.validator.models

import java.math.BigInteger

/** Container for decoded validator info. */
data class DecodedValidatorInfo(
    val decodedValidators: Map<String, Any?>,
    val totalWeight: BigInteger,
    val vthoTotalSupply: BigInteger,
    val vetPriceUsd: BigInteger,
    val vthoPriceUsd: BigInteger,
    val vthoBurned: BigInteger,
)
