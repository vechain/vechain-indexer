import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.b3tr.proposal.ProposalId

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "proposalId",
    description = "Proposal ID to filter by.",
    schema = Schema(type = "string", pattern = ProposalId.REGEX),
)
annotation class ProposalIdParameter(
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
