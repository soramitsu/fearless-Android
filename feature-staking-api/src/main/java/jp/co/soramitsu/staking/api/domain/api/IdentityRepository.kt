package jp.co.soramitsu.staking.api.domain.api

import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

interface IdentityRepository {

    suspend fun getIdentitiesFromIds(chain: Chain, accountIdsHex: List<String>): AccountIdMap<Identity?>

    suspend fun getIdentitiesFromAddresses(chain: Chain, accountAddresses: List<String>): AccountAddressMap<Identity?>

    suspend fun getIdentitiesFromIdsBytes(chain: Chain, accountIdsBytes: List<ByteArray>): AccountIdMap<Identity?>
}
