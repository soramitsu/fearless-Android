package jp.co.soramitsu.feature_onboarding_impl.presentation.terms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class TermsViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    private val _termsAddressLiveData = MutableLiveData<String>()
    val termsAddressLiveData: LiveData<String> = _termsAddressLiveData

    init {
        disposables.add(
            interactor.getTermsAddress()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _termsAddressLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }
}