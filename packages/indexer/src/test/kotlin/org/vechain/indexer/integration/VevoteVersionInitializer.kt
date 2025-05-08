package org.vechain.indexer.integration

import jakarta.annotation.PostConstruct
import javax.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexerVersion

@Component
@Profile("test")
class VevoteVersionInitializer(private val mongoTemplate: MongoTemplate) {
    @PostConstruct
    fun init() {
        // Create a version document for vevote_proposal_comments collection
        mongoTemplate.save(IndexerVersion("vevote_proposal_comments", 1), "indexer_versions")
    }
}
