package jp.co.soramitsu.feature_crowdloan_api.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.TrieIndex
import kotlinx.coroutines.flow.Flow

interface CrowdloanRepository {

    suspend fun isCrowdloansAvailable(): Boolean

    suspend fun allFundInfos(): Map<ParaId, FundInfo>

    suspend fun getWinnerInfo(funds: Map<ParaId, FundInfo>): Map<ParaId, Boolean>

    suspend fun getParachainMetadata(): Map<ParaId, ParachainMetadata>

    suspend fun getContribution(accountId: AccountId, paraId: ParaId, trieIndex: TrieIndex): Contribution?

    suspend fun blocksPerLeasePeriod(): BigInteger

    fun fundInfoFlow(parachainId: ParaId, networkType: Node.NetworkType): Flow<FundInfo>

    suspend fun minContribution(): BigInteger

    suspend fun checkRemark(apiUrl: String, apiKey: String, address: String): Boolean

    suspend fun saveEthAddress(paraId: ParaId, address: String, ethAddress: String)

    fun getEthAddress(paraId: ParaId, address: String): String?
}

class ParachainMetadata(
    val iconLink: String,
    val name: String,
    val description: String,
    val rewardRate: BigDecimal?,
    val website: String,
    val token: String,
    val flow: ParachainMetadataFlow?,
) {
    val isMoonbeam: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "moonbeam"
    val isAstar: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "astar"
    val isAcala: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "acala"
}

class ParachainMetadataFlow(
    val name: String?,
    val data: ParachainMetadataFlowData?
)

class ParachainMetadataFlowData(
    val apiUrl: String?,
    val apiKey: String?,
    val bonusUrl: String?,
    val termsUrl: String?,
    val crowdloanInfoUrl: String?,
    val fearlessReferral: String?,
    val totalReward: String?,
) {
    val baseUrl = apiUrl?.removePrefix("https://")
}
