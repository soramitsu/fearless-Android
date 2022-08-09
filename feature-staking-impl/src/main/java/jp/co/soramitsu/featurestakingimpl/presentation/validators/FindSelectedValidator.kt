package jp.co.soramitsu.featurestakingimpl.presentation.validators

import jp.co.soramitsu.featurestakingapi.domain.model.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun List<Validator>.findSelectedValidator(accountIdHex: String) = withContext(Dispatchers.Default) {
    firstOrNull { it.accountIdHex == accountIdHex }
}
