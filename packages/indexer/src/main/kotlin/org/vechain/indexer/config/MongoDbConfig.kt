package org.vechain.indexer.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer.repos"])
open class MongoDbConfig