package jp.co.soramitsu.feature_crowdloan_api.data.repository

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId

interface CrowdloanRepository {

    suspend fun allFundInfos(): Map<ParaId, FundInfo>

    suspend fun getParachainMetadata(): Map<ParaId, ParachainMetadata>
}

class ParachainMetadata(
    val iconLink: String,
    val name: String,
    val description: String
)
