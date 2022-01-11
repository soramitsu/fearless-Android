package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.LightMetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
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

    // TODO for compatibility only
    val selectedAccountMapping: Flow<Map<ChainId, Account?>>

    suspend fun getSelectedMetaAccount(): MetaAccount
    fun selectedMetaAccountFlow(): Flow<MetaAccount>
    suspend fun findMetaAccount(accountId: ByteArray): MetaAccount?

    suspend fun allMetaAccounts(): List<MetaAccount>

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>
    suspend fun selectMetaAccount(metaId: Long)
    suspend fun updateAccountPositions(accountOrdering: List<MetaAccountOrdering>)

    suspend fun getPreferredCryptoType(): CryptoType

    fun selectedNodeFlow(): Flow<Node>

    suspend fun getSelectedLanguage(): Language
    suspend fun changeSelectedLanguage(language: Language)

    suspend fun accountExists(accountId: AccountId): Boolean
    suspend fun getMetaAccount(metaId: Long): MetaAccount

    suspend fun updateMetaAccountName(metaId: Long, newName: String)
    suspend fun deleteMetaAccount(metaId: Long)
}
