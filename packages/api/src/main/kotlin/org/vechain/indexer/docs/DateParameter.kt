import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.validation.ISODateString

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "date",
    schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
    description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
)
annotation class DateParameter(
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
