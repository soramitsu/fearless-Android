package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.EncryptionType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getSourceTypes(): Single<List<SourceType>>

    fun getEncryptionTypes(): Single<List<EncryptionType>>
}