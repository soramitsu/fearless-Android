package jp.co.soramitsu.app.activity.domain

import io.reactivex.Single
import jp.co.soramitsu.app.navigation.Destination
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class MainInteractor(
    private val accountRepository: AccountRepository
) {

    fun getNavigationDestination(): Single<Destination> {
        return Single.fromCallable {
            val address = accountRepository.getExistingAddress()
            if (address == null) {
                Destination.ONBOARDING
            } else {
                Destination.MAIN
            }
        }
    }
}