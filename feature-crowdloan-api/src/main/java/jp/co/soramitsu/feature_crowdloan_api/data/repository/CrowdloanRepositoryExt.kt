package jp.co.soramitsu.feature_crowdloan_api.data.repository

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.TrieIndex

suspend fun CrowdloanRepository.getContributions(accountId: AccountId, keys: Map<ParaId, TrieIndex>): Map<ParaId, Contribution?> {
    return keys.mapValues { (paraId, trieIndex) ->
        getContribution(accountId, paraId, trieIndex)
    }
}
