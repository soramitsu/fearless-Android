package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
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
                        Crowdloan(
                            parachainMetadata = parachainMetadatas[parachainId],
                            raised = fundInfo.raised,
                            parachainId = parachainId,
                            cap = fundInfo.cap,
                            remainingTimeInMillis = expectedRemainingTime(currentBlockNumber, fundInfo.end, expectedBlockTime),
                            depositor = fundInfo.depositor
                        )
                    }
            }

            emitAll(withBlockUpdates)
        }
    }

    private fun expectedRemainingTime(
        currentBlock: BlockNumber,
        targetBlock: BlockNumber,
        expectedBlockTimeInMillis: BigInteger,
    ): Long {
        val blockDifference = targetBlock - currentBlock
        val expectedTimeDifference = blockDifference * expectedBlockTimeInMillis

        return expectedTimeDifference.toLong()
    }

    private fun FundInfo.isActive(currentBlockNumber: BlockNumber) = end > currentBlockNumber
}
