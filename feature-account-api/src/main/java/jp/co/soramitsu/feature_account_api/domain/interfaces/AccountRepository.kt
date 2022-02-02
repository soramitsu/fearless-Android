package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.LightMetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

class AccountAlreadyExistsException : Exception()

interface AccountRepository {

    fun getEncryptionTypes(): List<CryptoType>

    suspend fun selectAccount(metaAccountId: Long)

    fun selectedAccountFlow(): Flow<Account>

    suspend fun getSelectedAccount(): Account

    suspend fun getSelectedAccount(chainId: String): Account
    suspend fun getSelectedMetaAccount(): MetaAccount
    suspend fun getMetaAccount(metaId: Long): MetaAccount
    fun selectedMetaAccountFlow(): Flow<MetaAccount>

    suspend fun findMetaAccount(accountId: ByteArray): MetaAccount?

    suspend fun allMetaAccounts(): List<MetaAccount>

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>
    suspend fun selectMetaAccount(metaId: Long)

    suspend fun updateMetaAccountName(metaId: Long, newName: String)

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun isAccountSelected(): Boolean

    suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String
    )

    suspend fun deleteAccount(metaId: Long)

    suspend fun getAccounts(): List<Account>

    suspend fun getAccount(address: String): Account

    suspend fun getAccountOrNull(address: String): Account?

    suspend fun getMyAccounts(query: String, chainId: String): Set<Account>

    suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType
    )

    suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType
    )

    suspend fun importFromJson(
        json: String,
        password: String,
        name: String
    )

    suspend fun isCodeSet(): Boolean

    suspend fun savePinCode(code: String)

    suspend fun getPinCode(): String?

    suspend fun generateMnemonic(): List<String>

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

    suspend fun updateAccountsOrdering(accountOrdering: List<MetaAccountOrdering>)

    suspend fun processAccountJson(json: String): ImportJsonData

    fun getLanguages(): List<Language>

    suspend fun selectedLanguage(): Language

    suspend fun changeLanguage(language: Language)

    suspend fun getSecuritySource(accountAddress: String): SecuritySource

    suspend fun getMetaAccountSecrets(metaId: Long?): EncodableStruct<MetaAccountSecrets>?

    fun createQrAccountContent(payload: QrSharing.Payload): String

    suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String): String

    suspend fun isAccountExists(accountId: AccountId): Boolean

    suspend fun isInCurrentNetwork(address: String, chainId: ChainId): Boolean

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>
}
