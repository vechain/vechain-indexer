package org.vechain.indexer.thor.client

import org.vechain.indexer.thor.model.Block

/**
 * Client interface to access the Thorest RESTful API of the VeChain Thor Network.
 *
 * Use with default implementation {@link org.vechain.indexer.thor.client.DefaultThorClient.class
 * DefaultThorClient} or make you own implementation to pass onto the indexer.
 *
 * @see <a href="https://docs.vechain.org/thor/thorest-api">Thorest API</a>
 */
interface ThorClient {
    suspend fun getBlock(blockNumber: Long): Block

    suspend fun getBestBlock(): Block
}
