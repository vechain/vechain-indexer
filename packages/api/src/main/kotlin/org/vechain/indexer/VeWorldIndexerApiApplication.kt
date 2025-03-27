package org.vechain.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(scanBasePackages = ["org.vechain.indexer"])
open class VeWorldIndexerApiApplication

fun main(args: Array<String>) {
    runApplication<VeWorldIndexerApiApplication>(*args)
}
