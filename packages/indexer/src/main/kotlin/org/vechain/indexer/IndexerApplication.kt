package org.vechain.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["org.vechain.indexer"])
@EnableScheduling
open class IndexerApplication

fun main(args: Array<String>) {
    runApplication<IndexerApplication>(*args)
}
