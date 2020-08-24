package jp.co.soramitsu.app.main.domain

import io.reactivex.Single
import jp.co.soramitsu.app.main.navigation.Destination
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class MainInteractor(
    private val accountRepository: AccountRepository
) {

    fun getNavigationDestination(): Single<Destination> {
        return Single.fromCallable {
            val accountName = accountRepository.getExistingAccountName()
            if (accountName == null) {
                Destination.ONBOARDING
            } else {
                Destination.MAIN
            }
        }
    }
}