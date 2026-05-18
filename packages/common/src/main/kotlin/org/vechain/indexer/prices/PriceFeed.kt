package org.vechain.indexer.prices

private const val BYTES32_LENGTH = 32

/**
 * USD price feeds published by the vechain.energy `PriceFeedOracle` contract on mainnet.
 *
 * Each feed is keyed by a short ASCII identifier (e.g. `vet-usd`) that the on-chain contract
 * expects as a `bytes32` — the ASCII bytes right-padded with zeros to 32 bytes. See
 * https://learn.vechain.energy/vechain.energy/Oracles/ for the canonical list and per-feed status.
 */
enum class PriceFeed(private val feedKey: String) {
    VET_USD("vet-usd"),
    VTHO_USD("vtho-usd"),
    B3TR_USD("b3tr-usd"),
    EURT_USD("eurt-usd"),
    GBP_USD("gbp-usd"),
    BRL_USD("brl-usd");

    private val idBytes: ByteArray =
        feedKey
            .toByteArray(Charsets.US_ASCII)
            .also {
                require(it.size <= BYTES32_LENGTH) {
                    "Feed key '$feedKey' exceeds $BYTES32_LENGTH bytes"
                }
            }
            .copyOf(BYTES32_LENGTH)

    /**
     * Returns a fresh 32-byte representation of this feed's on-chain identifier, suitable for
     * passing to `PriceFeedOracle.getLatestValue(bytes32)`. A defensive copy is returned so callers
     * cannot mutate the shared backing array.
     */
    fun toBytes32(): ByteArray = idBytes.copyOf()
}
