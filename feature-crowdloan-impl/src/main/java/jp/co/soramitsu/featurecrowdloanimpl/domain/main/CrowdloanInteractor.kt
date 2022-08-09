package jp.co.soramitsu.featurecrowdloanimpl.domain.main

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featureaccountapi.domain.model.accountId
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.featurecrowdloanapi.data.repository.CrowdloanRepository
import jp.co.soramitsu.featurecrowdloanapi.data.repository.ParachainMetadata
import jp.co.soramitsu.featurecrowdloanapi.data.repository.getContributions
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.mapFundInfoToCrowdloan
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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
    private val chainStateRepository: ChainStateRepository
) {

    fun crowdloansFlow(chain: Chain): Flow<GroupedCrowdloans> {
        return flow {
            val chainId = chain.id

            if (crowdloanRepository.isCrowdloansAvailable(chainId).not()) {
                emit(emptyMap())

                return@flow
            }

            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata(chain)
            }.getOrDefault(emptyMap())

            val metaAccount = accountRepository.getSelectedMetaAccount()

            val accountId = metaAccount.accountId(chain)!! // TODO ethereum

            val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis(chainId)
            val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod(chainId)

            val withBlockUpdates = chainStateRepository.currentBlockNumberFlow(chainId).map { currentBlockNumber ->
                val fundInfos = crowdloanRepository.allFundInfos(chainId)

                val contributionKeys = fundInfos.mapValues { (_, fundInfo) -> fundInfo.fundIndex }

                val contributions = crowdloanRepository.getContributions(chainId, accountId, contributionKeys)
                val winnerInfo = crowdloanRepository.getWinnerInfo(chainId, fundInfos)

                val aa = fundInfos.values
                    .map { fundInfo ->
                        val paraId = fundInfo.paraId
                        val minContribution = crowdloanRepository.minContribution(chain.id)

                        mapFundInfoToCrowdloan(
                            fundInfo = fundInfo,
                            parachainMetadata = parachainMetadatas[paraId],
                            parachainId = paraId,
                            currentBlockNumber = currentBlockNumber,
                            expectedBlockTimeInMillis = expectedBlockTime,
                            blocksPerLeasePeriod = blocksPerLeasePeriod,
                            contribution = contributions[paraId],
                            hasWonAuction = winnerInfo.getValue(paraId),
                            minContribution = minContribution
                        )
                    }
                    .sortedWith(
                        compareByDescending<Crowdloan> { it.fundInfo.raised }
                            .thenBy { it.fundInfo.end }
                    )
                    .groupBy { it.state::class }
                    .toSortedMap(Crowdloan.State.STATE_CLASS_COMPARATOR)
                aa
            }

            emitAll(withBlockUpdates)
        }
    }

    suspend fun checkRemark(apiUrl: String, apiKey: String): Result<Boolean> = runCatching {
        val address = accountRepository.getSelectedAccount().address
        crowdloanRepository.checkRemark(apiUrl, apiKey, address)
    }
}
