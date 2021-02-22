package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import java.math.BigInteger

typealias AccountIdMap<V> = Map<String, V>

interface StakingRepository {

    suspend fun getTotalIssuance(): BigInteger

    suspend fun getActiveEraIndex(): BigInteger

    suspend fun getElectedValidatorsExposure(eraIndex: BigInteger): AccountIdMap<Exposure>

    suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger): AccountIdMap<ValidatorPrefs>

    suspend fun getIdentities(accountIdsHex: List<String>): AccountIdMap<Identity?>

    suspend fun getSlashes(accountIdsHex: List<String>): AccountIdMap<Boolean>
}