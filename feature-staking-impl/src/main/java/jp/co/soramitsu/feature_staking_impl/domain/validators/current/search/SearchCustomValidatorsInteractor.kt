package jp.co.soramitsu.feature_staking_impl.domain.validators.current.search

import android.annotation.SuppressLint
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchCustomValidatorsInteractor(
    private val validatorProvider: ValidatorProvider,
    private val accountRepository: AccountRepository
) {

    @SuppressLint("DefaultLocale")
    suspend fun searchValidator(query: String, localValidators: Collection<Validator>): List<Validator> = withContext(Dispatchers.Default) {
        val queryLower = query.toLowerCase()

        val searchInLocal = localValidators.filter {
            val foundInIdentity = it.identity?.display?.toLowerCase()?.contains(queryLower) ?: false

            it.address.startsWith(query) || foundInIdentity
        }

        if (searchInLocal.isNotEmpty()) {
            return@withContext searchInLocal
        }

        if (accountRepository.isInCurrentNetwork(query)) {
            val validator = validatorProvider.getValidatorWithoutElectedInfo(query)

            if (validator.prefs != null) {
                listOf(validator)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}
