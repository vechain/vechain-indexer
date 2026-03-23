package org.vechain.indexer.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.NumberSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.servers.Server
import org.bson.types.Decimal128
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.utils.SpringDocUtils
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
    }

    init {
        // Map Decimal128 to number schema so OpenAPI doesn't expose internal BSON structure
        SpringDocUtils.getConfig().replaceWithSchema(Decimal128::class.java, NumberSchema())
    }

    @Value("\${app.version:UNKNOWN}") lateinit var rawVersion: String

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
                            .schema(StringSchema().example("veworld-web")),
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
}
