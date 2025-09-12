import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "roundId",
    schema = Schema(type = "integer"),
    description = "Round ID to filter by.",
)
annotation class RoundIdParameter(
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
