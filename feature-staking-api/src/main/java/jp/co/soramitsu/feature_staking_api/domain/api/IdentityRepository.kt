package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.feature_staking_api.domain.model.Identity

interface IdentityRepository {

    suspend fun getIdentities(accountIdsHex: List<String>): AccountIdMap<Identity?>
}