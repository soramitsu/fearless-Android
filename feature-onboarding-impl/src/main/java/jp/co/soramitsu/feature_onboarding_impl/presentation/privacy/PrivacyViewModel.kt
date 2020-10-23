package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class PrivacyViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    private val _privacyAddressLiveData = MutableLiveData<String>()
    val privacyAddressLiveData: LiveData<String> = _privacyAddressLiveData

    init {
        disposables.add(
            interactor.getPrivacyAddress()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _privacyAddressLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }
}