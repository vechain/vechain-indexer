package org.vechain.indexer.config

import java.util.concurrent.ConcurrentHashMap
import org.bson.Document
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.ExecutableFindOperation
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.aggregation.TypedAggregation
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.data.mongodb.core.query.NearQuery
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.VersionedDocument

/**
 * A custom MongoTemplate that automatically excludes unwanted documents from all read queries
 * targeting [IndexedDocument] entities.
 *
 * Exclusion rules are composed in [buildExclusionCriteria]. Currently, this includes:
 * - **Checkpoint filter**: excludes checkpoint documents (which lack a `blockNumber` field)
 *
 * To add a new exclusion rule (e.g. archive filtering), add a [Criteria] to the list returned by
 * [buildExclusionCriteria]. The [addExclusionFilters] method automatically skips any criteria whose
 * field already appears in the query, preventing duplicate-key exceptions from Spring Data.
 *
 * **Not overridden** (intentionally):
 * - All write methods (`save`, `insert`, `remove`, `update*`) — the indexer writes checkpoints via
 *   these
 */
open class FilteringMongoTemplate(dbFactory: MongoDatabaseFactory, converter: MongoConverter) :
    MongoTemplate(dbFactory, converter) {

    companion object {
        internal const val PREVIOUS_VERSIONS_FIELD = "_previousVersions"
    }

    /** Cache: entity class → whether it implements [IndexedDocument]. */
    private val filterCache = ConcurrentHashMap<Class<*>, Boolean>()

    /**
     * Cache: entity class → custom @Id property name (or `null` if none / standard).
     *
     * A sentinel empty-string value is stored when the entity has no custom @Id property so that we
     * don't re-resolve it on every call.
     */
    private val idPropertyCache = ConcurrentHashMap<Class<*>, String>()

    /**
     * Cache: collection name → whether it belongs to an [IndexedDocument] entity. Resolved on
     * demand from the mapping context so that collection-name-only overloads work correctly
     * regardless of call ordering.
     */
    private val collectionFilterCache = ConcurrentHashMap<String, Boolean>()

    /** Cache: entity class → whether it implements [VersionedDocument]. */
    private val versionedCache = ConcurrentHashMap<Class<*>, Boolean>()

    internal fun shouldFilter(entityClass: Class<*>): Boolean =
        filterCache.getOrPut(entityClass) {
            IndexedDocument::class.java.isAssignableFrom(entityClass)
        }

    internal fun isVersionedDocument(entityClass: Class<*>): Boolean =
        versionedCache.getOrPut(entityClass) {
            VersionedDocument::class.java.isAssignableFrom(entityClass)
        }

    /**
     * Determines whether the given collection name belongs to an [IndexedDocument] entity by
     * consulting the mapping context. Results are cached per collection name.
     */
    /** Cache: collection name → whether it belongs to a [VersionedDocument] entity. */
    private val collectionVersionedCache = ConcurrentHashMap<String, Boolean>()

    internal fun shouldFilterCollection(collectionName: String): Boolean =
        collectionFilterCache.getOrPut(collectionName) {
            converter.mappingContext.persistentEntities.any { entity ->
                @Suppress("UNCHECKED_CAST") val mongoEntity = entity as? MongoPersistentEntity<*>
                mongoEntity != null &&
                    mongoEntity.collection == collectionName &&
                    IndexedDocument::class.java.isAssignableFrom(mongoEntity.type)
            }
        }

    internal fun isVersionedCollection(collectionName: String): Boolean =
        collectionVersionedCache.getOrPut(collectionName) {
            converter.mappingContext.persistentEntities.any { entity ->
                @Suppress("UNCHECKED_CAST") val mongoEntity = entity as? MongoPersistentEntity<*>
                mongoEntity != null &&
                    mongoEntity.collection == collectionName &&
                    VersionedDocument::class.java.isAssignableFrom(mongoEntity.type)
            }
        }

    // Check for both "_id" (MongoDB field name) and "id" (Java property name).
    // SimpleMongoRepository.findById builds queries using Criteria.where("id") — the
    // unmapped Java property name. If we only check for "_id" we miss these queries and
    // incorrectly add a checkpoint exclusion that overwrites the id match after field-name
    // mapping, causing findOne to return an arbitrary non-checkpoint document.
    //
    // Additionally, entities with a non-standard @Id property name (e.g. @Id val proposalId)
    // cause Spring Data to build queries using Criteria.where("proposalId"). We detect these
    // by inspecting the entity's mapping metadata (cached to avoid repeated reflection).
    internal fun queryHasIdCriteria(query: Query, entityClass: Class<*>? = null): Boolean {
        val queryObject = query.queryObject
        if (queryObject.containsKey("_id") || queryObject.containsKey("id")) return true
        if (entityClass != null) {
            val idPropertyName = cachedIdPropertyName(entityClass)
            if (idPropertyName != null && queryObject.containsKey(idPropertyName)) return true
        }
        return false
    }

    /**
     * Returns the custom @Id property name for the entity, or `null` if the entity uses the
     * standard "id" / "_id" mapping. Results are cached per class.
     */
    internal fun cachedIdPropertyName(entityClass: Class<*>): String? {
        val cached =
            idPropertyCache.getOrPut(entityClass) {
                val name =
                    converter.mappingContext.getPersistentEntity(entityClass)?.idProperty?.name
                if (name == null || name == "id" || name == "_id") "" else name
            }
        return cached.ifEmpty { null }
    }

    /**
     * Builds all exclusion criteria for an entity class. This is the extension point for adding new
     * exclusion rules (e.g. archive filtering).
     */
    internal fun buildExclusionCriteria(entityClass: Class<*>? = null): List<Criteria> {
        val criteria = mutableListOf<Criteria>()
        // Checkpoint filter: exclude docs that lack blockNumber (checkpoint docs don't have it)
        criteria.add(Criteria.where("blockNumber").exists(true))
        return criteria
    }

    /**
     * Adds a projection exclusion for `_previousVersions` on [VersionedDocument] entities so that
     * MongoDB never transfers version history data over the wire for normal reads.
     */
    internal fun addVersionExclusionProjection(query: Query, entityClass: Class<*>?) {
        if (entityClass != null && isVersionedDocument(entityClass)) {
            // If the query already uses inclusion projections, _previousVersions is
            // implicitly excluded — adding an explicit exclusion would cause MongoDB
            // error 31254 ("Cannot do exclusion on field in inclusion projection").
            val fieldsObject = query.fields().fieldsObject
            val hasInclusionProjection =
                fieldsObject.any { (key, value) -> key != "_id" && (value == 1 || value == true) }
            if (!hasInclusionProjection) {
                query.fields().exclude(PREVIOUS_VERSIONS_FIELD)
            }
        }
    }

    internal fun addExclusionFilters(query: Query, entityClass: Class<*>? = null): Query {
        if (queryHasIdCriteria(query, entityClass)) return query
        val queryObject = query.queryObject
        for (criteria in buildExclusionCriteria(entityClass)) {
            // Skip criteria whose field is already present in the query to avoid
            // Spring Data's duplicate key exception from addCriteria().
            val field = criteria.key
            if (field != null && queryObject.containsKey(field)) continue
            query.addCriteria(criteria)
        }
        return query
    }

    internal fun exclusionMatchStage():
        org.springframework.data.mongodb.core.aggregation.AggregationOperation {
        val criteria = buildExclusionCriteria()
        return Aggregation.match(Criteria().andOperator(*criteria.toTypedArray()))
    }

    internal fun prependExclusionFilter(
        aggregation: Aggregation,
        unsetVersions: Boolean = false,
    ): Aggregation {
        val existingOps = aggregation.pipeline.operations
        val newOps = mutableListOf<AggregationOperation>()
        newOps.add(exclusionMatchStage())
        newOps.addAll(existingOps)
        if (unsetVersions) {
            newOps.add(AggregationOperation { _ -> Document("\$unset", PREVIOUS_VERSIONS_FIELD) })
        }
        return Aggregation.newAggregation(newOps).withOptions(aggregation.options)
    }

    // --- Query-based read methods ---

    override fun <T : Any> find(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): List<T> {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
        addVersionExclusionProjection(query, entityClass)
        return super.find(query, entityClass, collectionName)
    }

    override fun <T : Any> findOne(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): T? {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
        addVersionExclusionProjection(query, entityClass)
        return super.findOne(query, entityClass, collectionName)
    }

    override fun count(query: Query, entityClass: Class<*>?, collectionName: String): Long {
        if (entityClass != null && shouldFilter(entityClass))
            addExclusionFilters(query, entityClass)
        return super.count(query, entityClass, collectionName)
    }

    override fun exists(query: Query, entityClass: Class<*>?, collectionName: String): Boolean {
        if (entityClass != null && shouldFilter(entityClass))
            addExclusionFilters(query, entityClass)
        return super.exists(query, entityClass, collectionName)
    }

    override fun <T : Any> stream(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): java.util.stream.Stream<T> {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
        addVersionExclusionProjection(query, entityClass)
        return super.stream(query, entityClass, collectionName)
    }

    override fun <T : Any> findDistinct(
        query: Query,
        field: String,
        collectionName: String,
        entityClass: Class<*>,
        resultClass: Class<T>,
    ): List<T> {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
        addVersionExclusionProjection(query, entityClass)
        return super.findDistinct(query, field, collectionName, entityClass, resultClass)
    }

    // --- No-query read methods ---

    override fun <T : Any> findAll(entityClass: Class<T>, collectionName: String): List<T> {
        if (shouldFilter(entityClass)) {
            val query = Query()
            addExclusionFilters(query)
            addVersionExclusionProjection(query, entityClass)
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
                prependExclusionFilter(aggregation, isVersionedDocument(inputType)),
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
        if (shouldFilterCollection(collectionName)) {
            return super.aggregate(
                prependExclusionFilter(aggregation, isVersionedCollection(collectionName)),
                collectionName,
                outputType,
            )
        }
        return super.aggregate(aggregation, collectionName, outputType)
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
                prependExclusionFilter(aggregation, isVersionedDocument(aggregation.inputType)),
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
                prependExclusionFilter(aggregation, isVersionedDocument(aggregation.inputType)),
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
                prependExclusionFilter(aggregation, isVersionedDocument(inputType)),
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
        if (shouldFilterCollection(collectionName)) {
            return super.aggregateStream(
                prependExclusionFilter(aggregation, isVersionedCollection(collectionName)),
                collectionName,
                outputType,
            )
        }
        return super.aggregateStream(aggregation, collectionName, outputType)
    }

    // --- Aggregation stream methods (TypedAggregation variants) ---

    override fun <O : Any> aggregateStream(
        aggregation: TypedAggregation<*>,
        outputType: Class<O>,
    ): java.util.stream.Stream<O> {
        if (shouldFilter(aggregation.inputType)) {
            val collectionName = getCollectionName(aggregation.inputType)
            return super.aggregateStream(
                prependExclusionFilter(aggregation, isVersionedDocument(aggregation.inputType)),
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
                prependExclusionFilter(aggregation, isVersionedDocument(aggregation.inputType)),
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
    // We intercept query() to wrap the returned ExecutableFind and add exclusion
    // filters in matching().

    override fun <T : Any> query(domainType: Class<T>): ExecutableFindOperation.ExecutableFind<T> {
        val base = super.query(domainType)
        if (!shouldFilter(domainType)) return base
        return FilteringExecutableFind(base, domainType)
    }

    /**
     * Wraps [ExecutableFindOperation.FindWithQuery] to add exclusion filters to every [matching]
     * call. Terminal operations without a preceding [matching] call are redirected through
     * `matching(Query())` so the filters are always applied.
     */
    private inner class FilteringFindWithQuery<T : Any>(
        private val delegate: ExecutableFindOperation.FindWithQuery<T>,
        private val entityClass: Class<*>,
    ) : ExecutableFindOperation.FindWithQuery<T> {

        override fun matching(query: Query): ExecutableFindOperation.TerminatingFind<T> {
            addVersionExclusionProjection(query, entityClass)
            return delegate.matching(addExclusionFilters(query, entityClass))
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
     * Wraps [ExecutableFindOperation.TerminatingDistinct] to add exclusion filters to every
     * [matching] call. Terminal [all] without a preceding [matching] is redirected through
     * `matching(Query())` so the filters are always applied.
     */
    private inner class FilteringTerminatingDistinct<T : Any>(
        private val delegate: ExecutableFindOperation.TerminatingDistinct<T>,
        private val entityClass: Class<*>,
    ) : ExecutableFindOperation.TerminatingDistinct<T> {

        override fun matching(query: Query): ExecutableFindOperation.TerminatingDistinct<T> {
            addVersionExclusionProjection(query, entityClass)
            return delegate.matching(addExclusionFilters(query, entityClass))
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
