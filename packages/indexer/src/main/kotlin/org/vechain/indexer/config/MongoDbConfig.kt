package org.vechain.indexer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer"])
open class MongoDbConfig(private val mongoTemplate: MongoTemplate) {

    @Bean("mongoTransactionManager")
    open fun mongoTransactionManager(): MongoTransactionManager {
        return MongoTransactionManager(mongoTemplate.mongoDatabaseFactory)
    }
}
