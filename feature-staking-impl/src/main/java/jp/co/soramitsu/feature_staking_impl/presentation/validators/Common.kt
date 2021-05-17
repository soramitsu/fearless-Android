package jp.co.soramitsu.feature_staking_impl.presentation.validators

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun List<Validator>.findSelectedValidator(accountIdHex: String) = withContext(Dispatchers.Default) {
    firstOrNull { it.accountIdHex == accountIdHex }
}
