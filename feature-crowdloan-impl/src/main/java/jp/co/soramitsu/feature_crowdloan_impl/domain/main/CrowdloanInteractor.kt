package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.isActive
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.mapFundInfoToCrowdloan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class Crowdloan(
    val depositor: AccountId,
    val parachainMetadata: ParachainMetadata?,
    val parachainId: BigInteger,
    val raised: BigInteger,
    val remainingTimeInMillis: Long,
    val cap: BigInteger,
)

@OptIn(ExperimentalTime::class)
val Crowdloan.remainingTimeInSeconds: Long
    get() = remainingTimeInMillis.milliseconds.inSeconds.toLong()

class CrowdloanInteractor(
    private val accountRepository: AccountRepository,
    private val crowdloanRepository: CrowdloanRepository,
    private val chainStateRepository: ChainStateRepository,
) {

    fun crowdloansFlow(): Flow<List<Crowdloan>> {
        return flow {
            val fundInfos = crowdloanRepository.allFundInfos()

            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata()
            }.getOrDefault(emptyMap())

            val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis()
            val networkType = accountRepository.currentNetworkType()

            val withBlockUpdates = chainStateRepository.currentBlockNumberFlow(networkType).map { currentBlockNumber ->
                fundInfos.entries.toList()
                    .filter { (_, fundInfo) -> fundInfo.isActive(currentBlockNumber) }
                    .map { (parachainId, fundInfo) ->
                        mapFundInfoToCrowdloan(
                            fundInfo = fundInfo,
                            parachainMetadata = parachainMetadatas[parachainId],
                            parachainId = parachainId,
                            currentBlockNumber = currentBlockNumber,
                            expectedBlockTimeInMillis = expectedBlockTime
                        )
                    }
            }

            emitAll(withBlockUpdates)
        }
    }
}
