package jp.co.soramitsu.crowdloan.api.data.repository

import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundIndex
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.runtime.AccountId

/** Read-only relay crowdloan state, kept for legacy crowdloan recovery. Contributing is no longer offered. */
interface CrowdloanRepository {

    suspend fun isCrowdloansAvailable(chainId: ChainId): Boolean

    suspend fun allFundInfos(chainId: ChainId): Map<ParaId, FundInfo>

    suspend fun getContribution(chainId: ChainId, accountId: AccountId, paraId: ParaId, fundIndex: FundIndex): Contribution?
}
