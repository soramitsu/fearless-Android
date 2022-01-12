package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface IdentityRepository {

    suspend fun getIdentitiesFromIds(chainId: ChainId, accountIdsHex: List<String>): AccountIdMap<Identity?>

    suspend fun getIdentitiesFromAddresses(chain: Chain, accountAddresses: List<String>): AccountAddressMap<Identity?>
}
