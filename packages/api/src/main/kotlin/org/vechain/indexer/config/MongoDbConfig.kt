package org.vechain.indexer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer"])
open class MongoDbConfig {

    @Bean
    open fun mongoTemplate(
        dbFactory: MongoDatabaseFactory,
        converter: MappingMongoConverter,
    ): MongoTemplate = CheckpointFilteringMongoTemplate(dbFactory, converter)
}
