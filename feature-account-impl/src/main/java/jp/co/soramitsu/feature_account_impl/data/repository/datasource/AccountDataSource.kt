package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource

interface AccountDataSource {

    fun saveAuthType(authType: AuthType)

    fun getAuthType(): AuthType

    fun savePinCode(pinCode: String)

    fun getPinCode(): String?

    fun saveSelectedNode(node: Node)

    fun getSelectedNode(): Node?

    fun saveSecuritySource(accountAddress: String, source: SecuritySource)

    fun getSecuritySource(accountAddress: String): SecuritySource?

    fun anyAccountSelected(): Boolean

    fun saveSelectedAccount(account: Account)

    fun observeSelectedAccount(): Observable<Account>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun observeSelectedNode(): Observable<Node>

    fun getSelectedLanguage(): Language

    fun changeSelectedLanguage(language: Language)
}