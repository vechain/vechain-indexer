package org.vechain.indexer.config.mongo

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.vechain.indexer.config.BigIntegerToDecimal128Converter
import org.vechain.indexer.config.Decimal128ToBigIntegerConverter
import org.vechain.indexer.config.FilteringMongoTemplate
import org.vechain.indexer.config.StringToBigIntegerConverter
import org.vechain.indexer.config.StringToDecimal128Converter

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = ["org.vechain.indexer"])
open class MongoDbConfig {

    @Bean
    open fun customConversions(): MongoCustomConversions =
        MongoCustomConversions(
            listOf(
                StringToDecimal128Converter(),
                StringToBigIntegerConverter(),
                Decimal128ToBigIntegerConverter(),
                BigIntegerToDecimal128Converter(),
            )
        )

    @Bean
    open fun mongoTemplate(
        dbFactory: MongoDatabaseFactory,
        converter: MappingMongoConverter,
    ): MongoTemplate = FilteringMongoTemplate(dbFactory, converter)

    @Bean("mongoTransactionManager")
    open fun mongoTransactionManager(mongoTemplate: MongoTemplate): MongoTransactionManager =
        MongoTransactionManager(mongoTemplate.mongoDatabaseFactory)
}
