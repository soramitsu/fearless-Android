package jp.co.soramitsu.feature_staking_impl.presentation.validators

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

suspend fun Flow<List<Validator>>.findSelectedValidator(accountIdHex: String) = withContext(Dispatchers.Default) {
    first().firstOrNull { it.accountIdHex == accountIdHex }
}
