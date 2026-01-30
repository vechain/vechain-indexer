package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.config.VeChainNetwork.MAINNET
import org.vechain.indexer.config.VeChainNetwork.TESTNET

@Configuration
@ConfigurationProperties(prefix = "vechain.forks")
open class ForkConfig {
    var galactica: ForkInfo = ForkInfo()
    var hayabusa: ForkInfo = ForkInfo()

    open class ForkInfo {
        var mainnet: Long = 0L
        var testnet: Long = 0L
    }

    fun getGalacticaBlock(network: VeChainNetwork): Long =
        when (network) {
            MAINNET -> galactica.mainnet
            TESTNET -> galactica.testnet
            else -> 0L
        }

    fun getHayabusaBlock(network: VeChainNetwork): Long =
        when (network) {
            MAINNET -> hayabusa.mainnet
            TESTNET -> hayabusa.testnet
            else -> 0L
        }
}
