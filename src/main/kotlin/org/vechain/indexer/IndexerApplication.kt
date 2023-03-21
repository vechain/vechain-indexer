package org.vechain.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan(basePackages = ["org.vechain.indexer"])
@EnableScheduling
class IndexerApplication

fun main(args: Array<String>) {
	runApplication<IndexerApplication>(*args)
}