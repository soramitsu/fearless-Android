package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

interface IdentityRepository {

    suspend fun getIdentitiesFromIds(chain: Chain, accountIdsHex: List<String>): AccountIdMap<Identity?>

    suspend fun getIdentitiesFromAddresses(chain: Chain, accountAddresses: List<String>): AccountAddressMap<Identity?>

    suspend fun getIdentitiesFromIdsBytes(chain: Chain, accountIdsBytes: List<ByteArray>): AccountIdMap<Identity?>
}
