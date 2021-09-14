package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import kotlinx.coroutines.flow.Flow

interface AccountDataSource : SecretStoreV1 {

    suspend fun saveAuthType(authType: AuthType)

    suspend fun getAuthType(): AuthType

    suspend fun savePinCode(pinCode: String)

    suspend fun getPinCode(): String?

    suspend fun saveSelectedNode(node: Node)

    suspend fun getSelectedNode(): Node?

    suspend fun anyAccountSelected(): Boolean

    suspend fun saveSelectedAccount(account: Account)

    fun selectedAccountFlow(): Flow<Account>

    suspend fun getSelectedAccount(): Account

    suspend fun getPreferredCryptoType(): CryptoType

    fun selectedNodeFlow(): Flow<Node>

    suspend fun getSelectedLanguage(): Language

    suspend fun changeSelectedLanguage(language: Language)
}
