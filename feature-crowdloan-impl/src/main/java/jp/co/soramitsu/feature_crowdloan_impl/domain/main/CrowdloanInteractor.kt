package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.getContributions
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.mapFundInfoToCrowdloan
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.state.chain
import jp.co.soramitsu.runtime.state.selectedChainFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

class Crowdloan(
    val parachainMetadata: ParachainMetadata?,
    val parachainId: BigInteger,
    val raisedFraction: BigDecimal,
    val state: State,
    val leasePeriodInMillis: Long,
    val leasedUntilInMillis: Long,
    val fundInfo: FundInfo,
    val myContribution: Contribution?
) {

    sealed class State {

        companion object {

            val STATE_CLASS_COMPARATOR = Comparator<KClass<out State>> { first, _ ->
                when (first) {
                    Active::class -> -1
                    Finished::class -> 1
                    else -> 0
                }
            }
        }

        object Finished : State()

        class Active(val remainingTimeInMillis: Long) : State()
    }
}

typealias GroupedCrowdloans = GroupedList<KClass<out Crowdloan.State>, Crowdloan>

class CrowdloanInteractor(
    private val accountRepository: AccountRepository,
    private val crowdloanRepository: CrowdloanRepository,
    private val crowdloanSharedState: CrowdloanSharedState,
    private val chainStateRepository: ChainStateRepository,
) {

    fun crowdloansFlow(): Flow<GroupedCrowdloans> {
        return crowdloanSharedState.selectedChainFlow().flatMapLatest { chain ->
            val chainId = chain.id

            if (crowdloanRepository.isCrowdloansAvailable(chainId).not()) {
               return@flatMapLatest flowOf(emptyMap())
            }

            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata(chain)
            }.getOrDefault(emptyMap())

            val metaAccount = accountRepository.getSelectedMetaAccount()

            val accountId = metaAccount.accountIdIn(chain)!! // TODO ethereum

            val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis(chainId)
            val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod(chainId)

            val withBlockUpdates = chainStateRepository.currentBlockNumberFlow(chainId).map { currentBlockNumber ->
                val fundInfos = crowdloanRepository.allFundInfos(chainId)

                val contributionKeys = fundInfos.mapValues { (_, fundInfo) -> fundInfo.trieIndex }

                val contributions = crowdloanRepository.getContributions(chainId, accountId, contributionKeys)
                val winnerInfo = crowdloanRepository.getWinnerInfo(chainId, fundInfos)

                fundInfos.values
                    .map { fundInfo ->
                        val paraId = fundInfo.paraId

                        mapFundInfoToCrowdloan(
                            fundInfo = fundInfo,
                            parachainMetadata = parachainMetadatas[paraId],
                            parachainId = paraId,
                            currentBlockNumber = currentBlockNumber,
                            expectedBlockTimeInMillis = expectedBlockTime,
                            blocksPerLeasePeriod = blocksPerLeasePeriod,
                            contribution = contributions[paraId],
                            hasWonAuction = winnerInfo.getValue(paraId)
                        )
                    }
                    .sortedWith(
                        compareByDescending<Crowdloan> { it.fundInfo.raised }
                            .thenBy { it.fundInfo.end }
                    )
                    .groupBy { it.state::class }
                    .toSortedMap(Crowdloan.State.STATE_CLASS_COMPARATOR)
            }

            withBlockUpdates
        }
    }
}
