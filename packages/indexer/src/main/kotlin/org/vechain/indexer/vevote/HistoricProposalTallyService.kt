package org.vechain.indexer.vevote

import com.mongodb.BasicDBObject
import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Profile("vevote-historic-proposals")
@Service
open class HistoricProposalTallyService(
    private val mongoTemplate: MongoTemplate,
    private val repository: HistoricProposalsRepository,
) {
    fun aggregateAllTallies() {
        val aggregation =
            newAggregation(
                unwind("choices"),
                group("proposalId", "contract", "choices").count().`as`("voteCount"),
                group("\$_id.proposalId", "\$_id.contract")
                    .push(BasicDBObject("choice", "\$_id.choices").append("count", "\$voteCount"))
                    .`as`("tallies")
                    .sum("voteCount")
                    .`as`("totalVotes"),
            )

        val results =
            mongoTemplate
                .aggregate(aggregation, "historic_proposals_votes", Document::class.java)
                .mappedResults

        results.forEach { doc ->
            val idDoc = doc.get("_id") as Document
            val proposalId = idDoc.getString("proposalId")
            val contract = idDoc.getString("contract")
            val compositeId = "$contract-$proposalId"

            val talliesMap =
                (doc["tallies"] as List<Document>).associate {
                    (it["choice"] as Int) to (it["count"] as Number).toLong()
                }

            val proposal = repository.findById(compositeId).orElse(null)

            val orderedTallies =
                proposal?.choices?.mapIndexed { idx, _ -> talliesMap[idx + 1] ?: 0L }
                    ?: (1..(talliesMap.keys.maxOrNull() ?: 0)).map { idx -> talliesMap[idx] ?: 0L }

            val totalVotes = (doc["totalVotes"] as Number).toLong()

            // Always upsert so the doc exists
            mongoTemplate.upsert(
                Query.query(Criteria.where("_id").`is`(compositeId)),
                Update()
                    .set("proposalId", proposalId)
                    .set("contract", contract)
                    .set("voteTallies", orderedTallies)
                    .set("totalVotes", totalVotes),
                HistoricProposals::class.java,
            )
        }
    }
}
