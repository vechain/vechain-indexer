package org.vechain.indexer.config

import java.util.concurrent.ConcurrentHashMap
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
import org.vechain.indexer.IndexedDocument.Companion.CHECKPOINT_ID
import org.vechain.indexer.VersionedDocument

/**
 * A custom MongoTemplate that automatically excludes unwanted documents from all read queries
 * targeting [IndexedDocument] entities.
 *
 * Exclusion rules are composed in [buildExclusionCriteria]. Currently, this includes:
 * - **Checkpoint filter**: excludes checkpoint documents (which lack a `blockNumber` field)
 * - **Archive filter**: excludes archive documents (`_isArchive: true`) for [VersionedDocument]
 *   entities
 *
 * The [addExclusionFilters] method automatically skips any criteria whose field already appears in
 * the query, preventing duplicate-key exceptions from Spring Data.
 *
 * **Not overridden** (intentionally):
 * - All write methods (`save`, `insert`, `remove`, `update*`) — the indexer writes checkpoints via
 *   these
 */
open class FilteringMongoTemplate(dbFactory: MongoDatabaseFactory, converter: MongoConverter) :
    MongoTemplate(dbFactory, converter) {

    /** Cache: entity class → whether it implements [IndexedDocument]. */
    private val filterCache = ConcurrentHashMap<Class<*>, Boolean>()

    /** Cache: entity class → whether it implements [VersionedDocument]. */
    private val archiveFilterCache = ConcurrentHashMap<Class<*>, Boolean>()

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

    /** Cache: collection name → whether it belongs to a [VersionedDocument] entity. */
    private val collectionArchiveFilterCache = ConcurrentHashMap<String, Boolean>()

    internal fun shouldFilter(entityClass: Class<*>): Boolean =
        filterCache.getOrPut(entityClass) {
            IndexedDocument::class.java.isAssignableFrom(entityClass)
        }

    /**
     * Determines whether the given collection name belongs to an [IndexedDocument] entity by
     * consulting the mapping context. Results are cached per collection name.
     */
    internal fun shouldFilterCollection(collectionName: String): Boolean =
        collectionFilterCache.getOrPut(collectionName) {
            converter.mappingContext.persistentEntities.any { entity ->
                @Suppress("UNCHECKED_CAST") val mongoEntity = entity as? MongoPersistentEntity<*>
                mongoEntity != null &&
                    mongoEntity.collection == collectionName &&
                    IndexedDocument::class.java.isAssignableFrom(mongoEntity.type)
            }
        }

    internal fun shouldFilterArchives(entityClass: Class<*>): Boolean =
        archiveFilterCache.getOrPut(entityClass) {
            VersionedDocument::class.java.isAssignableFrom(entityClass)
        }

    internal fun shouldFilterCollectionForArchives(collectionName: String): Boolean =
        collectionArchiveFilterCache.getOrPut(collectionName) {
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
     * Builds all exclusion criteria for an entity class. Includes:
     * - Checkpoint filter: excludes docs without `blockNumber`
     * - Archive filter: excludes docs with `_isArchive: true` (only for [VersionedDocument]
     *   entities)
     */
    internal fun buildExclusionCriteria(
        entityClass: Class<*>? = null,
        includeArchiveFilter: Boolean = entityClass != null && shouldFilterArchives(entityClass),
    ): List<Criteria> {
        val criteria = mutableListOf<Criteria>()
        // Checkpoint filter: exclude the checkpoint document by its known _id value.
        // We use _id directly rather than blockNumber.exists(true) because some entities
        // map @Id to blockNumber, causing Spring Data to translate the field to _id —
        // which always exists, making the exists(true) check match everything.
        criteria.add(Criteria.where("_id").ne(CHECKPOINT_ID))
        // Archive filter: exclude archive documents stored in the same collection
        if (includeArchiveFilter) {
            criteria.add(Criteria.where("_isArchive").ne(true))
        }
        return criteria
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

    internal fun exclusionMatchStage(
        entityClass: Class<*>? = null,
        includeArchiveFilter: Boolean = entityClass != null && shouldFilterArchives(entityClass),
    ): AggregationOperation {
        val criteria = buildExclusionCriteria(entityClass, includeArchiveFilter)
        return Aggregation.match(Criteria().andOperator(*criteria.toTypedArray()))
    }

    internal fun prependExclusionFilter(
        aggregation: Aggregation,
        entityClass: Class<*>? = null,
        includeArchiveFilter: Boolean = entityClass != null && shouldFilterArchives(entityClass),
    ): Aggregation {
        val existingOps = aggregation.pipeline.operations
        val newOps = mutableListOf<AggregationOperation>()
        newOps.add(exclusionMatchStage(entityClass, includeArchiveFilter))
        newOps.addAll(existingOps)
        return Aggregation.newAggregation(newOps).withOptions(aggregation.options)
    }

    // --- Query-based read methods ---

    override fun <T : Any> find(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): List<T> {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
        return super.find(query, entityClass, collectionName)
    }

    override fun <T : Any> findOne(
        query: Query,
        entityClass: Class<T>,
        collectionName: String,
    ): T? {
        if (shouldFilter(entityClass)) addExclusionFilters(query, entityClass)
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
        return super.findDistinct(query, field, collectionName, entityClass, resultClass)
    }

    // --- No-query read methods ---

    override fun <T : Any> findAll(entityClass: Class<T>, collectionName: String): List<T> {
        if (shouldFilter(entityClass)) {
            val query = Query()
            addExclusionFilters(query, entityClass)
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
                prependExclusionFilter(aggregation, inputType),
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
            val archiveFilter = shouldFilterCollectionForArchives(collectionName)
            return super.aggregate(
                prependExclusionFilter(aggregation, includeArchiveFilter = archiveFilter),
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
                prependExclusionFilter(aggregation, aggregation.inputType),
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
                prependExclusionFilter(aggregation, aggregation.inputType),
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
                prependExclusionFilter(aggregation, inputType),
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
            val archiveFilter = shouldFilterCollectionForArchives(collectionName)
            return super.aggregateStream(
                prependExclusionFilter(aggregation, includeArchiveFilter = archiveFilter),
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
                prependExclusionFilter(aggregation, aggregation.inputType),
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
                prependExclusionFilter(aggregation, aggregation.inputType),
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

        override fun matching(query: Query): ExecutableFindOperation.TerminatingFind<T> =
            delegate.matching(addExclusionFilters(query, entityClass))

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

        override fun matching(query: Query): ExecutableFindOperation.TerminatingDistinct<T> =
            delegate.matching(addExclusionFilters(query, entityClass))

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
