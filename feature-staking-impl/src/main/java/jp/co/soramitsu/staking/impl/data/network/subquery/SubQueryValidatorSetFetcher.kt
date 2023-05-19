package jp.co.soramitsu.staking.impl.data.network.subquery

import jp.co.soramitsu.common.base.errors.RewardsNotSupportedWarning
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.historicalEras
import java.math.BigInteger

class SubQueryValidatorSetFetcher(
    private val stakingApi: StakingApi,
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val chainRegistry: ChainRegistry
) {

    suspend fun fetchAllValidators(chainId: ChainId, stashAccountAddress: String): List<String> {
        val historicalRange = stakingRepository.historicalEras(chainId)

        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        val stakingType = chain.externalApi?.staking?.type

        return when {
            stakingUrl == null -> throw RewardsNotSupportedWarning()
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                getSubqueryValidators(stakingUrl, stashAccountAddress, historicalRange)
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                getSubsquidCollators(stakingUrl, stashAccountAddress, historicalRange)
            }
            else -> throw RewardsNotSupportedWarning()
        }
    }

    private suspend fun getSubsquidCollators(
        stakingUrl: String,
        stashAccountAddress: String,
        historicalRange: List<BigInteger>
    ): List<String> {
        return emptyList()
    }

    private suspend fun getSubqueryValidators(
        stakingUrl: String,
        stashAccountAddress: String,
        historicalRange: List<BigInteger>
    ): List<String> {
        val validatorsInfos = stakingApi.getValidatorsInfo(
            stakingUrl,
            StakingEraValidatorInfosRequest(
                eraFrom = historicalRange.first(),
                eraTo = historicalRange.last(),
                accountAddress = stashAccountAddress
            )
        )

        return validatorsInfos.data.query?.eraValidatorInfos?.nodes?.map(
            Node::address
        )?.distinct().orEmpty()
    }
}
