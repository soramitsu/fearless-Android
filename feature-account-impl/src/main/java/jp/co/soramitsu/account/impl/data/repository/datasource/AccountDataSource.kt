package jp.co.soramitsu.account.impl.data.repository.datasource

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow

interface AccountDataSource : SecretStoreV1 {

    suspend fun saveAuthType(authType: AuthType)

    suspend fun getAuthType(): AuthType

    suspend fun savePinCode(pinCode: String)

    suspend fun getPinCode(): String?

    suspend fun anyAccountSelected(): Boolean

    // TODO for compatibility only
    val selectedAccountMapping: Flow<Map<ChainId, Account?>>

    suspend fun getSelectedMetaAccount(): MetaAccount
    fun selectedMetaAccountFlow(): Flow<MetaAccount>
    suspend fun findMetaAccount(accountId: ByteArray): MetaAccount?

    suspend fun allMetaAccounts(): List<MetaAccount>

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>
    suspend fun selectMetaAccount(metaId: Long)
    suspend fun updateAccountPositions(accountOrdering: List<MetaAccountOrdering>)

    suspend fun getPreferredCryptoTypeOrSelected(metaId: Long? = null): CryptoType

    suspend fun getSelectedLanguage(): Language
    suspend fun changeSelectedLanguage(language: Language)

    suspend fun accountExists(accountId: AccountId): Boolean
    suspend fun getMetaAccount(metaId: Long): MetaAccount

    suspend fun updateMetaAccountName(metaId: Long, newName: String)
    suspend fun updateMetaAccountBackedUp(metaId: Long)
    suspend fun updateWalletOnGoogleBackupDelete(metaId: Long)
    suspend fun deleteMetaAccount(metaId: Long)

    fun observeAllMetaAccounts(): Flow<List<MetaAccount>>
    fun lightMetaAccountFlow(metaId: Long): Flow<LightMetaAccount>
    fun selectedLightMetaAccount(): Flow<LightMetaAccount>
    suspend fun getSelectedLightMetaAccount(): LightMetaAccount
    suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount
    fun observeFavoriteChains(metaId: Long): Flow<List<FavoriteChainLocal>>
}
