package org.vechain.indexer

import io.mongock.runner.springboot.EnableMongock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableMongock
@SpringBootApplication(scanBasePackages = ["org.vechain.indexer"])
@EnableScheduling
open class IndexerApplication

fun main(args: Array<String>) {
    runApplication<IndexerApplication>(*args)
}
