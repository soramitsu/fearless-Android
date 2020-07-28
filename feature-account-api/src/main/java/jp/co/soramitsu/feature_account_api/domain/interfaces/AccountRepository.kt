package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Single

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>
}