package jp.co.soramitsu.feature_crowdloan_impl.domain.main

import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import java.math.BigInteger

class Crowdloan(
    val parachainMetadata: ParachainMetadata?,
    val parachainId: BigInteger,
    val raised: BigInteger,
    val cap: BigInteger
)

class CrowdloanInteractor(
    private val crowdloanRepository: CrowdloanRepository
) {

    suspend fun getAllCrowdloans(): List<Crowdloan> {
        val fundInfos = crowdloanRepository.allFundInfos()
        val parachainMetadatas = crowdloanRepository.getParachainMetadata()

        return fundInfos.entries.map { (parachainId, fundInfo) ->
            Crowdloan(
                parachainMetadata = parachainMetadatas[parachainId],
                raised = fundInfo.raised,
                parachainId = parachainId,
                cap = fundInfo.cap
            )
        }
    }
}
