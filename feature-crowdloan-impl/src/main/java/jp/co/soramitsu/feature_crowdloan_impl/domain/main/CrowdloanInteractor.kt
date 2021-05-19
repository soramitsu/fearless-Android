package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigInteger

class Crowdloan(
    val depositor: AccountId,
    val parachainMetadata: ParachainMetadata?,
    val parachainId: BigInteger,
    val raised: BigInteger,
    val cap: BigInteger
)

class CrowdloanInteractor(
    private val crowdloanRepository: CrowdloanRepository,
) {

    fun crowdloansFlow(): Flow<List<Crowdloan>> {
        return flow {
            val fundInfos = crowdloanRepository.allFundInfos()
            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata()
            }.getOrDefault(emptyMap())

            val data = fundInfos.entries.map { (parachainId, fundInfo) ->
                Crowdloan(
                    parachainMetadata = parachainMetadatas[parachainId],
                    raised = fundInfo.raised,
                    parachainId = parachainId,
                    cap = fundInfo.cap,
                    depositor = fundInfo.depositor
                )
            }

            emit(data)
        }
    }
}
