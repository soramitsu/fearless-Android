package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.mapFundInfoToCrowdloan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    val fundInfo: FundInfo
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
    private val chainStateRepository: ChainStateRepository,
) {

    fun crowdloansFlow(): Flow<GroupedCrowdloans> {
        return flow {
            val fundInfos = crowdloanRepository.allFundInfos()

            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata()
            }.getOrDefault(emptyMap())

            val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis()
            val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod()
            val networkType = accountRepository.currentNetworkType()

            val withBlockUpdates = chainStateRepository.currentBlockNumberFlow(networkType).map { currentBlockNumber ->
                fundInfos.entries.toList()
                    .map { (parachainId, fundInfo) ->
                        mapFundInfoToCrowdloan(
                            fundInfo = fundInfo,
                            parachainMetadata = parachainMetadatas[parachainId],
                            parachainId = parachainId,
                            currentBlockNumber = currentBlockNumber,
                            expectedBlockTimeInMillis = expectedBlockTime,
                            blocksPerLeasePeriod = blocksPerLeasePeriod
                        )
                    }.groupBy { it.state::class }
                    .toSortedMap(Crowdloan.State.STATE_CLASS_COMPARATOR)
            }

            emitAll(withBlockUpdates)
        }
    }
}
