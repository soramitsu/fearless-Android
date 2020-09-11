package jp.co.soramitsu.splash.presentation

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.splash.SplashRouter

class SplashViewModel(
    private val router: SplashRouter,
    private val repository: AccountRepository
) : BaseViewModel() {
    init {
        openInitialDestination()
    }

    private fun openInitialDestination() {
        disposables += repository.isAccountSelected()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isSelected ->
                if (isSelected) {
                    router.openPin()
                } else {
                    router.openOnboarding()
                }
            }
    }
}