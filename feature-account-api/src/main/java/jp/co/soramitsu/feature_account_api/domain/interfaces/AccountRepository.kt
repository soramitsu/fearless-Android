package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getSourceTypes(): Single<List<SourceType>>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getDefaultNodes(): Single<List<Node>>

    fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable

    fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable

    fun importFromJson(json: String, password: String, node: Node): Completable
}