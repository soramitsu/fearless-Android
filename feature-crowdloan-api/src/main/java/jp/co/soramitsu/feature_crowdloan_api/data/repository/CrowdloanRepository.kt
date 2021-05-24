package jp.co.soramitsu.feature_crowdloan_api.data.repository

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger

interface CrowdloanRepository {

    fun crowdloanAvailableFlow(): Flow<Boolean>

    suspend fun allFundInfos(): Map<ParaId, FundInfo>

    suspend fun getParachainMetadata(): Map<ParaId, ParachainMetadata>

    suspend fun blocksPerLeasePeriod(): BigInteger

    fun fundInfoFlow(parachainId: ParaId, networkType: Node.NetworkType): Flow<FundInfo>
}

class ParachainMetadata(
    val iconLink: String,
    val name: String,
    val description: String,
    val rewardRate: BigDecimal,
    val website: String,
    val token: String
)
