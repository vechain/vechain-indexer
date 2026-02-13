package org.vechain.indexer.config

import org.springframework.data.domain.ScrollPosition
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.ExecutableFindOperation
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.aggregation.TypedAggregation
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.data.mongodb.core.query.NearQuery
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.VersionedDocument

/**
 * A custom MongoTemplate that automatically excludes `__checkpoint__` documents and `_isArchive`
 * documents from all read queries targeting [IndexedDocument] / [VersionedDocument] entities.
 *
 * Every data collection contains a checkpoint document used by the indexer to track progress.
 * Stateful collections also store archive documents (previous versions) in the same collection,
 * distinguished by `_isArchive: true`. This template ensures that neither checkpoint documents nor
 * archive documents are ever returned by read operations, removing the need for manual exclusion in
 * every query.
 *
 * **Not overridden** (intentionally):
 * - All write methods (`save`, `insert`, `remove`, `update*`) — the indexer writes checkpoints and
 *   archives via these or via raw driver calls
 */
open class FilteringMongoTemplate(dbFactory: MongoDatabaseFactory, converter: MongoConverter) :
    MongoTemplate(dbFactory, converter) {

    private fun shouldFilter(entityClass: Class<*>): Boolean =
        IndexedDocument::class.java.isAssignableFrom(entityClass)

    private fun shouldFilterArchives(entityClass: Class<*>): Boolean =
        VersionedDocument::class.java.isAssignableFrom(entityClass)

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

    private fun addArchiveExclusion(query: Query, entityClass: Class<*>? = null): Query {
        if (queryHasIdCriteria(query, entityClass)) return query
        query.addCriteria(Criteria.where("_isArchive").ne(true))
        return query
    }

    private fun checkpointMatchStage():
        org.springframework.data.mongodb.core.aggregation.AggregationOperation =
        Aggregation.match(Criteria.where("_id").ne(IndexedDocument.CHECKPOINT_ID))

    private fun archiveMatchStage():
        org.springframework.data.mongodb.core.aggregation.AggregationOperation =
        Aggregation.match(Criteria.where("_isArchive").ne(true))

    private fun prependCheckpointFilter(
        aggregation: Aggregation,
        includeArchiveFilter: Boolean = false,
    ): Aggregation {
        val existingOps = aggregation.pipeline.operations
        val newOps =
            mutableListOf<org.springframework.data.mongodb.core.aggregation.AggregationOperation>()
        newOps.add(checkpointMatchStage())
        if (includeArchiveFilter) newOps.add(archiveMatchStage())
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
        if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
        return super.find(query, entityClass, collectionName)
    }

    override fun <T : Any> findOne(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): T? {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
        return super.findOne(query, entityClass, collectionName)
    }

    override fun count(query: Query, entityClass: Class<*>?, collectionName: String): Long {
        if (entityClass != null && shouldFilter(entityClass))
            addCheckpointExclusion(query, entityClass)
        if (entityClass != null && shouldFilterArchives(entityClass))
            addArchiveExclusion(query, entityClass)
        return super.count(query, entityClass, collectionName)
    }

    override fun exists(query: Query, entityClass: Class<*>?, collectionName: String): Boolean {
        if (entityClass != null && shouldFilter(entityClass))
            addCheckpointExclusion(query, entityClass)
        if (entityClass != null && shouldFilterArchives(entityClass))
            addArchiveExclusion(query, entityClass)
        return super.exists(query, entityClass, collectionName)
    }

    override fun <T : Any> stream(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): java.util.stream.Stream<T> {
        if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
        if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
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
        if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
        return super.findDistinct(query, field, collectionName, entityClass, resultClass)
    }

    // --- No-query read methods ---

    override fun <T : Any> findAll(entityClass: Class<T>, collectionName: String): List<T> {
        if (shouldFilter(entityClass) || shouldFilterArchives(entityClass)) {
            val query = Query()
            if (shouldFilter(entityClass)) addCheckpointExclusion(query)
            if (shouldFilterArchives(entityClass)) addArchiveExclusion(query)
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
            return super.aggregate(
                prependCheckpointFilter(aggregation, shouldFilterArchives(inputType)),
                inputType,
                outputType,
            )
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
            return super.aggregate(
                prependCheckpointFilter(aggregation, shouldFilterArchives(aggregation.inputType)),
                collectionName,
                outputType,
            )
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
                prependCheckpointFilter(aggregation, shouldFilterArchives(aggregation.inputType)),
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
                prependCheckpointFilter(aggregation, shouldFilterArchives(inputType)),
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
                prependCheckpointFilter(aggregation, shouldFilterArchives(aggregation.inputType)),
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
                prependCheckpointFilter(aggregation, shouldFilterArchives(aggregation.inputType)),
                inputCollectionName,
                outputType,
            )
        }
        return super.aggregateStream(aggregation, inputCollectionName, outputType)
    }

    // --- Fluent find API (query()) ---
    // Spring Data MongoDB's @Query-annotated repository methods use the fluent API
    // (template.query(type).matching(query).all()) via AbstractMongoQuery. This path
    // goes through ExecutableFindOperationSupport which calls a package-private
    // MongoTemplate.doFind() variant, completely bypassing the overridden find() above.
    // We intercept query() to wrap the returned ExecutableFind and add checkpoint
    // exclusion in matching().

    override fun <T : Any> query(domainType: Class<T>): ExecutableFindOperation.ExecutableFind<T> {
        val base = super.query(domainType)
        if (!shouldFilter(domainType) && !shouldFilterArchives(domainType)) return base
        return FilteringExecutableFind(base, domainType)
    }

    /**
     * Wraps [ExecutableFindOperation.FindWithQuery] to add checkpoint exclusion to every [matching]
     * call. Terminal operations without a preceding [matching] call are redirected through
     * `matching(Query())` so the filter is always applied.
     */
    private inner class FilteringFindWithQuery<T : Any>(
        private val delegate: ExecutableFindOperation.FindWithQuery<T>,
        private val entityClass: Class<*>,
    ) : ExecutableFindOperation.FindWithQuery<T> {

        override fun matching(query: Query): ExecutableFindOperation.TerminatingFind<T> {
            if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
            if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
            return delegate.matching(query)
        }

        override fun matching(
            criteriaDefinition: CriteriaDefinition
        ): ExecutableFindOperation.TerminatingFind<T> = matching(Query.query(criteriaDefinition))

        override fun near(nearQuery: NearQuery) = delegate.near(nearQuery)

        // Terminal operations — redirect through matching() to guarantee the filter is applied.
        override fun oneValue(): T? = matching(Query()).oneValue()

        override fun firstValue(): T? = matching(Query()).firstValue()

        override fun all(): List<T> = matching(Query()).all()

        override fun stream(): java.util.stream.Stream<T> = matching(Query()).stream()

        override fun scroll(position: ScrollPosition) = matching(Query()).scroll(position)

        override fun count(): Long = matching(Query()).count()

        override fun exists(): Boolean = matching(Query()).exists()
    }

    /**
     * Wraps [ExecutableFindOperation.TerminatingDistinct] to add checkpoint exclusion to every
     * [matching] call. Terminal [all] without a preceding [matching] is redirected through
     * `matching(Query())` so the filter is always applied.
     */
    private inner class FilteringTerminatingDistinct<T : Any>(
        private val delegate: ExecutableFindOperation.TerminatingDistinct<T>,
        private val entityClass: Class<*>,
    ) : ExecutableFindOperation.TerminatingDistinct<T> {

        override fun matching(query: Query): ExecutableFindOperation.TerminatingDistinct<T> {
            if (shouldFilter(entityClass)) addCheckpointExclusion(query, entityClass)
            if (shouldFilterArchives(entityClass)) addArchiveExclusion(query, entityClass)
            return delegate.matching(query)
        }

        override fun matching(
            criteriaDefinition: CriteriaDefinition
        ): ExecutableFindOperation.TerminatingDistinct<T> =
            matching(Query.query(criteriaDefinition))

        override fun <R : Any> `as`(
            resultType: Class<R>
        ): ExecutableFindOperation.TerminatingDistinct<R> =
            FilteringTerminatingDistinct(delegate.`as`(resultType), entityClass)

        override fun all(): List<T> = matching(Query()).all()
    }

    private inner class FilteringFindWithProjection<T : Any>(
        private val delegate: ExecutableFindOperation.FindWithProjection<T>,
        private val entityClass: Class<*>,
    ) :
        ExecutableFindOperation.FindWithProjection<T>,
        ExecutableFindOperation.FindWithQuery<T> by FilteringFindWithQuery(delegate, entityClass) {

        override fun <R : Any> `as`(
            resultType: Class<R>
        ): ExecutableFindOperation.FindWithQuery<R> =
            FilteringFindWithQuery(delegate.`as`(resultType), entityClass)

        override fun distinct(field: String) =
            FilteringTerminatingDistinct(delegate.distinct(field), entityClass)
    }

    private inner class FilteringExecutableFind<T : Any>(
        private val delegate: ExecutableFindOperation.ExecutableFind<T>,
        private val entityClass: Class<*>,
    ) :
        ExecutableFindOperation.ExecutableFind<T>,
        ExecutableFindOperation.FindWithQuery<T> by FilteringFindWithQuery(delegate, entityClass) {

        override fun inCollection(
            collection: String
        ): ExecutableFindOperation.FindWithProjection<T> =
            FilteringFindWithProjection(delegate.inCollection(collection), entityClass)

        override fun <R : Any> `as`(
            resultType: Class<R>
        ): ExecutableFindOperation.FindWithQuery<R> =
            FilteringFindWithQuery(delegate.`as`(resultType), entityClass)

        override fun distinct(field: String) =
            FilteringTerminatingDistinct(delegate.distinct(field), entityClass)
    }
}
