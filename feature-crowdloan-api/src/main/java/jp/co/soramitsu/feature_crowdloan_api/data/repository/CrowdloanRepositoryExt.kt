package jp.co.soramitsu.feature_crowdloan_api.data.repository

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.TrieIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

suspend fun CrowdloanRepository.getContributions(
    accountId: AccountId,
    keys: Map<ParaId, TrieIndex>
): Map<ParaId, Contribution?> = withContext(Dispatchers.Default) {
    keys.map { (paraId, trieIndex) ->
        async { paraId to getContribution(accountId, paraId, trieIndex) }
    }
        .awaitAll()
        .toMap()
}
