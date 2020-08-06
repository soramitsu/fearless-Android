package jp.co.soramitsu.feature_onboarding_api.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface OnboardingInteractor {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getSourceTypes(): Single<List<SourceType>>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getDefaultNodes(): Single<List<Node>>
}