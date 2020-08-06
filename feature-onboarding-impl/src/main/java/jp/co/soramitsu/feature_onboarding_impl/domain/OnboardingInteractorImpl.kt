package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor

class OnboardingInteractorImpl(
    private val accountRepository: AccountRepository
) : OnboardingInteractor {

    override fun getTermsAddress(): Single<String> {
        return accountRepository.getTermsAddress()
    }

    override fun getPrivacyAddress(): Single<String> {
        return accountRepository.getPrivacyAddress()
    }

    override fun getSourceTypes(): Single<List<SourceType>> {
        return accountRepository.getSourceTypes()
    }

    override fun getEncryptionTypes(): Single<List<CryptoType>> {
        return accountRepository.getEncryptionTypes()
    }
}