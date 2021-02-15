package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import kotlinx.coroutines.flow.Flow

interface AccountDataSource {

    suspend fun saveAuthType(authType: AuthType)

    suspend fun getAuthType(): AuthType

    suspend fun savePinCode(pinCode: String)

    suspend fun getPinCode(): String?

    suspend fun saveSelectedNode(node: Node)

    suspend fun getSelectedNode(): Node?

    suspend fun saveSecuritySource(accountAddress: String, source: SecuritySource)

    suspend fun getSecuritySource(accountAddress: String): SecuritySource?

    suspend fun anyAccountSelected(): Boolean

    suspend fun saveSelectedAccount(account: Account)

    fun selectedAccountFlow(): Flow<Account>

    suspend fun getSelectedAccount(): Account

    suspend fun getPreferredCryptoType(): CryptoType

    fun selectedNodeFlow(): Flow<Node>

    suspend fun getSelectedLanguage(): Language

    suspend fun changeSelectedLanguage(language: Language)
}