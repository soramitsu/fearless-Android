package jp.co.soramitsu.featurecrowdloanapi.data.repository

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.FundIndex
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

suspend fun CrowdloanRepository.getContributions(
    chainId: ChainId,
    accountId: AccountId,
    keys: Map<ParaId, FundIndex>
): Map<ParaId, Contribution?> = withContext(Dispatchers.Default) {
    keys.map { (paraId, fundIndex) ->
        async { paraId to getContribution(chainId, accountId, paraId, fundIndex) }
    }
        .awaitAll()
        .toMap()
}

suspend fun CrowdloanRepository.hasWonAuction(chainId: ChainId, fundInfo: FundInfo): Boolean {
    val paraId = fundInfo.paraId

    return getWinnerInfo(chainId, mapOf(paraId to fundInfo)).getValue(paraId)
}
