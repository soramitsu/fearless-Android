package jp.co.soramitsu.featurecrowdloanapi.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.FundIndex
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface CrowdloanRepository {

    suspend fun isCrowdloansAvailable(chainId: ChainId): Boolean

    suspend fun allFundInfos(chainId: ChainId): Map<ParaId, FundInfo>

    suspend fun getWinnerInfo(chainId: ChainId, funds: Map<ParaId, FundInfo>): Map<ParaId, Boolean>

    suspend fun getParachainMetadata(chain: Chain): Map<ParaId, ParachainMetadata>

    suspend fun getContribution(chainId: ChainId, accountId: AccountId, paraId: ParaId, fundIndex: FundIndex): Contribution?

    suspend fun blocksPerLeasePeriod(chainId: ChainId): BigInteger

    fun fundInfoFlow(chainId: ChainId, parachainId: ParaId): Flow<FundInfo>

    suspend fun minContribution(chainId: ChainId): BigInteger

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
    val disabled: Boolean = false,
    val flow: ParachainMetadataFlow?
) {
    val isMoonbeam: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "moonbeam"
    val isAstar: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "astar"
    val isAcala: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "acala"
    val isInterlay: Boolean
        get() = flow?.name?.toLowerCase(Locale.getDefault()) == "interlay"
}

class ParachainMetadataFlow(
    val name: String?,
    val data: Map<String, Any?>?
)
