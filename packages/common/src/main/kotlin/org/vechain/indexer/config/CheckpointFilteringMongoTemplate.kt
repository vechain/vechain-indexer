package org.vechain.indexer.config

import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.aggregation.TypedAggregation
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexedDocument

/**
 * A custom MongoTemplate that automatically excludes `__checkpoint__` documents from all read
 * queries targeting [IndexedDocument] entities.
 *
 * Every data collection contains a checkpoint document used by the indexer to track progress. This
 * template ensures that checkpoint documents are never returned by read operations, removing the
 * need for manual exclusion in every query.
 *
 * **Not overridden** (intentionally):
 * - All write methods (`save`, `insert`, `remove`, `update*`) — the indexer writes checkpoints via
 *   these
 */
open class CheckpointFilteringMongoTemplate(
    dbFactory: MongoDatabaseFactory,
    converter: MongoConverter,
) : MongoTemplate(dbFactory, converter) {

    private fun shouldFilter(entityClass: Class<*>): Boolean =
        IndexedDocument::class.java.isAssignableFrom(entityClass)

    // Check for both "_id" (MongoDB field name) and "id" (Java property name).
    // SimpleMongoRepository.findById builds queries using Criteria.where("id") — the
    // unmapped Java property name. If we only check for "_id" we miss these queries and
    // incorrectly add a checkpoint exclusion that overwrites the id match after field-name
    // mapping, causing findOne to return an arbitrary non-checkpoint document.
    //
    // Additionally, entities with a non-standard @Id property name (e.g. @Id val proposalId)
    // cause Spring Data to build queries using Criteria.where("proposalId"). We detect these
    // by inspecting the entity's mapping metadata.
    private fun queryHasIdCriteria(query: Query, entityClass: Class<*>? = null): Boolean {
        val queryObject = query.queryObject
        if (queryObject.containsKey("_id") || queryObject.containsKey("id")) return true
        if (entityClass != null) {
            val entity = converter.mappingContext.getPersistentEntity(entityClass)
            val idPropertyName = entity?.idProperty?.name
            if (idPropertyName != null && queryObject.containsKey(idPropertyName)) return true
        }
        return false
    }

    private fun addCheckpointExclusion(query: Query, entityClass: Class<*>? = null): Query {
        if (queryHasIdCriteria(query, entityClass)) return query
        query.addCriteria(Criteria.where("_id").ne(IndexedDocument.CHECKPOINT_ID))
        return query
    }

    private fun checkpointMatchStage():
        org.springframework.data.mongodb.core.aggregation.AggregationOperation =
        Aggregation.match(Criteria.where("_id").ne(IndexedDocument.CHECKPOINT_ID))

    private fun prependCheckpointFilter(aggregation: Aggregation): Aggregation {
        val existingOps = aggregation.pipeline.operations
        val newOps =
            mutableListOf<org.springframework.data.mongodb.core.aggregation.AggregationOperation>()
        newOps.add(checkpointMatchStage())
        newOps.addAll(existingOps)
        return Aggregation.newAggregation(newOps).withOptions(aggregation.options)
    }

    // --- Query-based read methods ---

    override fun <T : Any> find(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): List<T> {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        return super.find(query, entityClass, collectionName)
    }

    override fun <T : Any> findOne(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): T? {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        return super.findOne(query, entityClass, collectionName)
    }

    override fun count(query: Query, entityClass: Class<*>?, collectionName: String): Long {
        if (entityClass != null && shouldFilter(entityClass))
            addCheckpointExclusion(query, entityClass)
        return super.count(query, entityClass, collectionName)
    }

    override fun exists(query: Query, entityClass: Class<*>?, collectionName: String): Boolean {
        if (entityClass != null && shouldFilter(entityClass))
            addCheckpointExclusion(query, entityClass)
        return super.exists(query, entityClass, collectionName)
    }

    override fun <T : Any> stream(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): java.util.stream.Stream<T> {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        return super.stream(query, entityClass, collectionName)
    }

    override fun <T : Any> findDistinct(
        query: Query,
        field: String,
        collectionName: String,
        entityClass: Class<*>,
        resultClass: Class<T>,
    ): List<T> {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        return super.findDistinct(query, field, collectionName, entityClass, resultClass)
    }

    // --- No-query read methods ---

    override fun <T : Any> findAll(entityClass: Class<T>, collectionName: String): List<T> {
        if (shouldFilter(entityClass)) {
            val query = Query()
            addCheckpointExclusion(query)
            return super.find(query, entityClass, collectionName)
        }
        return super.findAll(entityClass, collectionName)
    }

    // --- Aggregation methods (Aggregation variants) ---

    override fun <O : Any> aggregate(
        aggregation: Aggregation,
        inputType: Class<*>,
        outputType: Class<O>,
    ): AggregationResults<O> {
        if (shouldFilter(inputType)) {
            return super.aggregate(prependCheckpointFilter(aggregation), inputType, outputType)
        }
        return super.aggregate(aggregation, inputType, outputType)
    }

    override fun <O : Any> aggregate(
        aggregation: Aggregation,
        collectionName: String,
        outputType: Class<O>,
    ): AggregationResults<O> {
        return super.aggregate(prependCheckpointFilter(aggregation), collectionName, outputType)
    }

    // --- Aggregation methods (TypedAggregation variants) ---
    // Spring Data's @Aggregation annotation processing creates TypedAggregation objects,
    // which dispatch to these overloads rather than the Aggregation ones above.

    override fun <O : Any> aggregate(
        aggregation: TypedAggregation<*>,
        outputType: Class<O>,
    ): AggregationResults<O> {
        if (shouldFilter(aggregation.inputType)) {
            val collectionName = getCollectionName(aggregation.inputType)
            return super.aggregate(prependCheckpointFilter(aggregation), collectionName, outputType)
        }
        return super.aggregate(aggregation, outputType)
    }

    override fun <O : Any> aggregate(
        aggregation: TypedAggregation<*>,
        inputCollectionName: String,
        outputType: Class<O>,
    ): AggregationResults<O> {
        if (shouldFilter(aggregation.inputType)) {
            return super.aggregate(
                prependCheckpointFilter(aggregation),
                inputCollectionName,
                outputType,
            )
        }
        return super.aggregate(aggregation, inputCollectionName, outputType)
    }

    // --- Aggregation stream methods (Aggregation variants) ---

    override fun <O : Any> aggregateStream(
        aggregation: Aggregation,
        inputType: Class<*>,
        outputType: Class<O>,
    ): java.util.stream.Stream<O> {
        if (shouldFilter(inputType)) {
            return super.aggregateStream(
                prependCheckpointFilter(aggregation),
                inputType,
                outputType,
            )
        }
        return super.aggregateStream(aggregation, inputType, outputType)
    }

    override fun <O : Any> aggregateStream(
        aggregation: Aggregation,
        collectionName: String,
        outputType: Class<O>,
    ): java.util.stream.Stream<O> {
        return super.aggregateStream(
            prependCheckpointFilter(aggregation),
            collectionName,
            outputType,
        )
    }

    // --- Aggregation stream methods (TypedAggregation variants) ---

    override fun <O : Any> aggregateStream(
        aggregation: TypedAggregation<*>,
        outputType: Class<O>,
    ): java.util.stream.Stream<O> {
        if (shouldFilter(aggregation.inputType)) {
            val collectionName = getCollectionName(aggregation.inputType)
            return super.aggregateStream(
                prependCheckpointFilter(aggregation),
                collectionName,
                outputType,
            )
        }
        return super.aggregateStream(aggregation, outputType)
    }

    override fun <O : Any> aggregateStream(
        aggregation: TypedAggregation<*>,
        inputCollectionName: String,
        outputType: Class<O>,
    ): java.util.stream.Stream<O> {
        if (shouldFilter(aggregation.inputType)) {
            return super.aggregateStream(
                prependCheckpointFilter(aggregation),
                inputCollectionName,
                outputType,
            )
        }
        return super.aggregateStream(aggregation, inputCollectionName, outputType)
    }
}
