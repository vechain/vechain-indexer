package org.vechain.indexer.config.mongo

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.vechain.indexer.config.FilteringMongoTemplate

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = ["org.vechain.indexer"])
open class MongoDbConfig {

    @Bean
    open fun mongoTemplate(
        dbFactory: MongoDatabaseFactory,
        converter: MappingMongoConverter,
    ): MongoTemplate = FilteringMongoTemplate(dbFactory, converter)

    @Bean("mongoTransactionManager")
    open fun mongoTransactionManager(mongoTemplate: MongoTemplate): MongoTransactionManager =
        MongoTransactionManager(mongoTemplate.mongoDatabaseFactory)

    @Bean
    open fun diagnosticCommandListenerCustomizer(
        @Value("\${indexer.diagnostic-command-listener.enabled:false}") enabled: Boolean
    ): MongoClientSettingsBuilderCustomizer = MongoClientSettingsBuilderCustomizer { builder ->
        if (enabled) {
            builder.addCommandListener(DiagnosticCommandListener())
        }
    }
}
