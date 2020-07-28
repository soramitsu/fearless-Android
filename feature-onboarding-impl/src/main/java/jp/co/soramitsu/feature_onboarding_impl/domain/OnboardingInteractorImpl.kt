package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor

class OnboardingInteractorImpl(
    private val accountRepository: AccountRepository
) : OnboardingInteractor {

    override fun saveAccountName(accountName: String): Completable {
        return Completable.complete()
    }

    override fun getTermsAddress(): Single<String> {
        return accountRepository.getTermsAddress()
    }

    override fun getPrivacyAddress(): Single<String> {
        return accountRepository.getPrivacyAddress()
    }
}