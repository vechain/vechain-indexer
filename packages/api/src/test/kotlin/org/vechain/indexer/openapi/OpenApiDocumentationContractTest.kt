package org.vechain.indexer.openapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.vechain.indexer.accounts.AccountOverviewReadRepository
import org.vechain.indexer.accounts.AccountTotalsReadRepository
import org.vechain.indexer.accounts.VetBalanceReadRepository
import org.vechain.indexer.b3tr.action.ActionReadRepository
import org.vechain.indexer.b3tr.balance.B3trBalanceReadRepository
import org.vechain.indexer.b3tr.challenges.ChallengeReadRepository
import org.vechain.indexer.b3tr.gm.GmNftReadRepository
import org.vechain.indexer.b3tr.navigator.NavigatorReadRepository
import org.vechain.indexer.b3tr.proposal.ProposalCommentReadRepository
import org.vechain.indexer.b3tr.proposal.ProposalResultReadRepository
import org.vechain.indexer.b3tr.treasury.TreasuryTransferReadRepository
import org.vechain.indexer.b3tr.xAlloc.XAllocResultReadRepository
import org.vechain.indexer.blocks.BlocksReadRepository
import org.vechain.indexer.contracts.ContractReadRepository
import org.vechain.indexer.explorer.AverageFeesPerUserReadRepository
import org.vechain.indexer.explorer.BlockUsageReadRepository
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.nft.NftReadRepository
import org.vechain.indexer.safe.SafeReadRepository
import org.vechain.indexer.stargate.staking.NftHoldersReadRepository
import org.vechain.indexer.stargate.staking.VetStakedReadRepository
import org.vechain.indexer.stargate.token.StargateTokenReadRepository
import org.vechain.indexer.stargate.tokenReward.TokenRewardReadRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedReadRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedReadRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedReadRepository
import org.vechain.indexer.transfer.TransferReadRepository
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.ValidatorBlockReadRepository
import org.vechain.indexer.validator.ValidatorReadRepository
import org.vechain.indexer.vevote.HistoricProposalsReadRepository
import org.vechain.indexer.vevote.VeVoteCommentReadRepository
import org.vechain.indexer.vevote.VeVoteResultReadRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = OpenApiActiveProfilesResolver::class)
@TestPropertySource(
    properties = ["de.flapdoodle.mongodb.embedded.version=7.0.14", "postgres.enabled=false"]
)
class OpenApiDocumentationContractTest {

    // The block and transaction controllers need a reader; the spec comes from annotations.
    @MockitoBean private lateinit var postgresReadRepository: BlocksReadRepository
    @MockitoBean private lateinit var nftReadRepository: NftReadRepository
    @MockitoBean private lateinit var contractReadRepository: ContractReadRepository
    @MockitoBean private lateinit var gmNftReadRepository: GmNftReadRepository
    @MockitoBean private lateinit var b3trBalanceReadRepository: B3trBalanceReadRepository
    @MockitoBean private lateinit var actionReadRepository: ActionReadRepository
    @MockitoBean private lateinit var accountOverviewReadRepository: AccountOverviewReadRepository
    @MockitoBean private lateinit var accountTotalsReadRepository: AccountTotalsReadRepository
    @MockitoBean private lateinit var vetBalanceReadRepository: VetBalanceReadRepository
    @MockitoBean private lateinit var blockUsageReadRepository: BlockUsageReadRepository
    @MockitoBean private lateinit var averageFeesReadRepository: AverageFeesPerUserReadRepository
    @MockitoBean private lateinit var treasuryReadRepository: TreasuryTransferReadRepository
    @MockitoBean private lateinit var transferReadRepository: TransferReadRepository
    @MockitoBean private lateinit var xAllocReadRepository: XAllocResultReadRepository
    @MockitoBean private lateinit var historyReadRepository: HistoryReadRepository
    @MockitoBean private lateinit var validatorReadRepository: ValidatorReadRepository
    @MockitoBean private lateinit var validatorBlockReadRepository: ValidatorBlockReadRepository
    @MockitoBean private lateinit var delegationReadRepository: DelegationReadRepository
    @MockitoBean private lateinit var stargateTokenReadRepository: StargateTokenReadRepository
    @MockitoBean private lateinit var tokenRewardReadRepository: TokenRewardReadRepository
    @MockitoBean private lateinit var vetDelegatedReadRepository: VetDelegatedReadRepository
    @MockitoBean private lateinit var vthoGeneratedReadRepository: VthoGeneratedReadRepository
    @MockitoBean private lateinit var vthoClaimedReadRepository: VthoClaimedReadRepository
    @MockitoBean private lateinit var vetStakedReadRepository: VetStakedReadRepository
    @MockitoBean private lateinit var nftHoldersReadRepository: NftHoldersReadRepository
    @MockitoBean
    private lateinit var historicProposalsReadRepository: HistoricProposalsReadRepository
    @MockitoBean private lateinit var proposalResultReadRepository: ProposalResultReadRepository
    @MockitoBean private lateinit var proposalCommentReadRepository: ProposalCommentReadRepository
    @MockitoBean private lateinit var safeReadRepository: SafeReadRepository
    @MockitoBean private lateinit var challengeReadRepository: ChallengeReadRepository
    @MockitoBean private lateinit var navigatorReadRepository: NavigatorReadRepository
    @MockitoBean private lateinit var vevoteCommentReadRepository: VeVoteCommentReadRepository
    @MockitoBean private lateinit var vevoteResultReadRepository: VeVoteResultReadRepository

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `history address parameters are documented with address patterns`() {
        val spec = fetchSpec()
        val parameters = spec.at("/paths/~1api~1v2~1history~1{account}/get/parameters")

        expectThat(findParameter(parameters, "account", "path").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(parameters, "contractAddress", "query").at("/schema/pattern").asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
    }

    @Test
    fun `stargate token history route is present without a contractAddress filter`() {
        val spec = fetchSpec()
        val operation = spec.at("/paths/~1api~1v1~1stargate~1tokens~1{tokenId}~1history/get")
        val parameters = operation.at("/parameters")
        val eventNamesEnum =
            findParameter(parameters, "eventName", "query")
                .at("/schema/items/enum")
                .map { it.asText() }
                .toList()

        expectThat(operation.isMissingNode).isFalse()
        expectThat(findParameter(parameters, "tokenId", "path").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[A-Fa-f0-9]+$")
        expectThat(eventNamesEnum.contains("STARGATE_UNSTAKE")).isTrue()
        expectThat(eventNamesEnum.contains("NFT_SALE")).isTrue()
        expectThat(eventNamesEnum.contains("VEVOTE_VOTE_CAST")).isTrue()
        expectThat(parameters.firstOrNull { it.path("name").asText() == "contractAddress" } == null)
            .isTrue()
    }

    @Test
    fun `nft history route is present with required contract and token filters`() {
        val spec = fetchSpec()
        val operation = spec.at("/paths/~1api~1v1~1nfts~1history/get")
        val parameters = operation.at("/parameters")
        val eventNamesEnum =
            findParameter(parameters, "eventName", "query")
                .at("/schema/items/enum")
                .map { it.asText() }
                .toList()

        expectThat(operation.isMissingNode).isFalse()
        expectThat(
                findParameter(parameters, "contractAddress", "query").at("/required").asBoolean()
            )
            .isTrue()
        expectThat(
                findParameter(parameters, "contractAddress", "query").at("/schema/pattern").asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(findParameter(parameters, "tokenId", "query").at("/required").asBoolean())
            .isTrue()
        expectThat(findParameter(parameters, "tokenId", "query").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[A-Fa-f0-9]+$")
        expectThat(eventNamesEnum).isEqualTo(listOf("TRANSFER_NFT", "NFT_SALE"))
    }

    @Test
    fun `transfer and nft address query parameters are documented with address patterns`() {
        val transferParameters = specParameters("/paths/~1api~1v1~1transfers/get/parameters")
        val nftParameters = specParameters("/paths/~1api~1v1~1nfts/get/parameters")

        expectThat(
                findParameter(transferParameters, "address", "query").at("/schema/pattern").asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(transferParameters, "tokenAddress", "query")
                    .at("/schema/pattern")
                    .asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(findParameter(nftParameters, "address", "query").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(nftParameters, "contractAddress", "query")
                    .at("/schema/pattern")
                    .asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
    }

    @Test
    fun `common api responses document problem json for 404 and 500`() {
        val spec = fetchSpec()
        val responses = spec.at("/paths/~1api~1v2~1history~1{account}/get/responses")

        expectThat(contentTypes(responses.at("/404/content")).contains("application/json")).isTrue()
        expectThat(contentTypes(responses.at("/404/content")).contains("application/problem+json"))
            .isTrue()
        expectThat(contentTypes(responses.at("/500/content")).contains("application/json")).isTrue()
        expectThat(contentTypes(responses.at("/500/content")).contains("application/problem+json"))
            .isTrue()
    }

    @Test
    fun `profile gated public routes are present in generated spec`() {
        val spec = fetchSpec()

        expectThat(spec.at("/paths/~1api~1v1~1blocks/get").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1transactions~1latest/get").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1transfers~1latest/get").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1validators").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1validators~1blocks~1missed").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1validators~1delegations~1count").isMissingNode)
            .isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1contracts~1{address}").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1contracts~1by-master~1{address}").isMissingNode)
            .isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1vevote~1proposals~1comments").isMissingNode).isFalse()
    }

    @Test
    fun `project id header is documented on public endpoints`() {
        val parameters = specParameters("/paths/~1api~1v1~1validators/get/parameters")

        val projectIdHeader = parameters.firstOrNull {
            it.path("\$ref").asText() == "#/components/parameters/XProjectIdHeader"
        }

        expectThat(projectIdHeader != null).isTrue()
    }

    @Test
    fun `latest transactions route documents cursor pagination and hides transaction index`() {
        val spec = fetchSpec()
        val operation = spec.at("/paths/~1api~1v1~1transactions~1latest/get")
        val parameters = operation.at("/parameters")
        val transactionSchema = spec.at("/components/schemas/IndexedTransaction")
        val requiredFields = transactionSchema.at("/required").map { it.asText() }

        expectThat(operation.isMissingNode).isFalse()
        expectThat(findParameter(parameters, "size", "query").isMissingNode).isFalse()
        expectThat(findParameter(parameters, "cursor", "query").isMissingNode).isFalse()
        expectThat(parameters.firstOrNull { it.path("name").asText() == "direction" } == null)
            .isTrue()
        expectThat(
                spec
                    .at("/components/schemas/IndexedTransaction/properties/transactionIndex")
                    .isMissingNode
            )
            .isTrue()
        expectThat(requiredFields.contains("clauseCount")).isTrue()
        expectThat(requiredFields.contains("clauses")).isFalse()
        expectThat(requiredFields.contains("outputs")).isFalse()
        expectThat(transactionSchema.at("/properties/clauses/description").asText())
            .isEqualTo("Only returned when expanded=true.")
        expectThat(transactionSchema.at("/properties/outputs/description").asText())
            .isEqualTo("Only returned when expanded=true.")
    }

    @Test
    fun `latest transfer route documents cursor pagination and hides transfer index`() {
        val spec = fetchSpec()
        val operation = spec.at("/paths/~1api~1v1~1transfers~1latest/get")
        val parameters = operation.at("/parameters")

        expectThat(operation.isMissingNode).isFalse()
        expectThat(findParameter(parameters, "size", "query").isMissingNode).isFalse()
        expectThat(findParameter(parameters, "cursor", "query").isMissingNode).isFalse()
        expectThat(findParameter(parameters, "eventType", "query").isMissingNode).isFalse()
        expectThat(parameters.firstOrNull { it.path("name").asText() == "direction" } == null)
            .isTrue()
        expectThat(
                spec
                    .at("/components/schemas/IndexedTransferEvent/properties/transferIndex")
                    .isMissingNode
            )
            .isTrue()
    }

    @Test
    fun `blocks route documents from and size and has no direction`() {
        val parameters = specParameters("/paths/~1api~1v1~1blocks/get/parameters")

        expectThat(findParameter(parameters, "from", "query").isMissingNode).isFalse()
        expectThat(findParameter(parameters, "size", "query").isMissingNode).isFalse()
        expectThat(parameters.firstOrNull { it.path("name").asText() == "direction" } == null)
            .isTrue()
    }

    private fun specParameters(pointer: String): JsonNode = fetchSpec().at(pointer)

    private fun fetchSpec(): JsonNode {
        return objectMapper.readTree(fetchSpecResponse().contentAsString)
    }

    private fun fetchSpecResponse() =
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk).andReturn().response

    private fun findParameter(parameters: JsonNode, name: String, location: String): JsonNode =
        parameters.firstOrNull {
            it.path("name").asText() == name && it.path("in").asText() == location
        } ?: error("Parameter '$name' in '$location' not found")

    private fun contentTypes(content: JsonNode): List<String> =
        content.fieldNames().asSequence().toList()
}
