package org.vechain.indexer.config

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.servers.Server
import java.math.BigInteger
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class SwaggerConfig {
    companion object {
        private const val PROJECT_ID_HEADER_NAME = "X-Project-Id"
        private const val PROJECT_ID_PARAMETER_COMPONENT_NAME = "XProjectIdHeader"
        private const val PROJECT_ID_PARAMETER_REF =
            "#/components/parameters/$PROJECT_ID_PARAMETER_COMPONENT_NAME"
        private const val INDEXED_TRANSACTION_SCHEMA_NAME = "IndexedTransaction"
        private val TRANSACTION_EXPANDED_ONLY_FIELDS = setOf("clauses", "outputs")
        private const val BIG_INTEGER_PATTERN = "^-?[0-9]+$"
    }

    @Value("\${app.version:UNKNOWN}") lateinit var rawVersion: String

    /** JacksonConfig writes every BigInteger as a decimal string; a fresh schema per field. */
    @Bean
    open fun bigIntegerAsStringConverter(): ModelConverter =
        object : ModelConverter {
            override fun resolve(
                type: AnnotatedType,
                context: ModelConverterContext,
                chain: Iterator<ModelConverter>,
            ): Schema<*>? =
                if (Json.mapper().constructType(type.type).rawClass == BigInteger::class.java) {
                    StringSchema().pattern(BIG_INTEGER_PATTERN)
                } else if (chain.hasNext()) {
                    chain.next().resolve(type, context, chain)
                } else {
                    null
                }
        }

    @Bean
    open fun customOpenAPI(): OpenAPI {
        val semver = rawVersion.removePrefix("v.")
        return OpenAPI()
            .components(
                Components()
                    .addParameters(
                        PROJECT_ID_PARAMETER_COMPONENT_NAME,
                        Parameter()
                            .name(PROJECT_ID_HEADER_NAME)
                            .`in`("header")
                            .required(false)
                            .description(
                                "Optional caller/project identifier used for observability and " +
                                    "usage tracking."
                            )
                            .schema(StringSchema()),
                    )
            )
            .info(
                Info()
                    .title("VeWorld Indexer API")
                    .version(semver)
                    .description("Blockchain data indexed for fast querying")
            )
            .servers(
                listOf(
                    Server().url("/").description("Local"),
                    Server().url("https://indexer.mainnet.vechain.org").description("Mainnet"),
                    Server().url("https://indexer.testnet.vechain.org").description("Testnet"),
                )
            )
    }

    @Bean
    open fun xProjectIdHeaderCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.paths.orEmpty().values.forEach { pathItem ->
            pathItem.readOperations().forEach { operation ->
                val alreadyDocumented =
                    operation.parameters?.any {
                        it.name == PROJECT_ID_HEADER_NAME || it.`$ref` == PROJECT_ID_PARAMETER_REF
                    } ?: false

                if (!alreadyDocumented) {
                    operation.addParametersItem(Parameter().`$ref`(PROJECT_ID_PARAMETER_REF))
                }
            }
        }
    }

    /** Raw-string descriptions keep their source indent, which Markdown renders as code. */
    @Bean
    open fun trimDescriptionIndentCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.paths
            .orEmpty()
            .values
            .flatMap { it.readOperations() }
            .forEach { operation ->
                operation.description = operation.description?.trimIndent()
            }
    }

    @Bean
    open fun transactionExpandedOnlyFieldsCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val transactionSchema =
                openApi.components?.schemas?.get(INDEXED_TRANSACTION_SCHEMA_NAME)
                    ?: return@OpenApiCustomizer

            transactionSchema.required =
                transactionSchema.required
                    ?.filterNot { it in TRANSACTION_EXPANDED_ONLY_FIELDS }
                    ?.toMutableList()

            TRANSACTION_EXPANDED_ONLY_FIELDS.forEach { field ->
                transactionSchema.properties?.get(field)?.description =
                    "Only returned when expanded=true."
            }
        }
}
