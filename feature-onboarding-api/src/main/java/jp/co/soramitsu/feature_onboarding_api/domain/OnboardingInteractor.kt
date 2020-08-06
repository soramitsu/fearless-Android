package jp.co.soramitsu.feature_onboarding_api.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.EncryptionType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface OnboardingInteractor {

    fun saveAccountName(accountName: String): Completable

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getSourceTypes(): Single<List<SourceType>>

    fun getEncryptionTypes(): Single<List<EncryptionType>>
}