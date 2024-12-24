package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class AccountAlreadyExistsException : Exception()

interface AccountRepository {

    fun getEncryptionTypes(): List<CryptoType>

    suspend fun selectAccount(metaAccountId: Long)

    suspend fun getSelectedAccount(chainId: ChainId): Account
    suspend fun getSelectedMetaAccount(): MetaAccount
    suspend fun getMetaAccount(metaId: Long): MetaAccount
    fun selectedMetaAccountFlow(): Flow<MetaAccount>

    suspend fun findMetaAccount(accountId: ByteArray): MetaAccount?

    suspend fun allMetaAccounts(): List<MetaAccount>

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>
    suspend fun selectMetaAccount(metaId: Long)

    suspend fun updateMetaAccountName(metaId: Long, newName: String)

    suspend fun updateMetaAccountBackedUp(metaId: Long)

    suspend fun updateWalletOnGoogleBackupDelete(metaId: Long)

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun isAccountSelected(): Boolean

    suspend fun createAccount(payload: AddAccountPayload): Long

    suspend fun deleteAccount(metaId: Long)

    suspend fun importFromSeed(
        walletId: Long?,
        seed: String?,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    )

    fun validateJsonBackup(json: String, password: String)

    suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    )

    suspend fun importAdditionalFromJson(
        walletId: Long,
        ethJson: String,
        password: String
    )

    suspend fun isCodeSet(): Boolean

    suspend fun savePinCode(code: String)

    suspend fun getPinCode(): String?

    suspend fun generateMnemonic(length: Mnemonic.Length): List<String>

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

    suspend fun updateAccountsOrdering(accountOrdering: List<MetaAccountOrdering>)

    suspend fun processAccountJson(json: String): ImportJsonData

    fun getLanguages(): List<Language>

    suspend fun selectedLanguage(): Language

    suspend fun changeLanguage(language: Language)

    suspend fun getSecuritySource(accountAddress: String): SecuritySource

    suspend fun getChainAccountSecrets(metaId: Long?, chainId: ChainId): EncodableStruct<ChainAccountSecrets>?

    suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String): String

    suspend fun isAccountExists(accountId: AccountId): Boolean

    suspend fun isInCurrentNetwork(address: String, chainId: ChainId): Boolean

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>
    fun googleAddressAllWalletsFlow(): Flow<List<String>>
    suspend fun googleBackupAddressForWallet(walletId: Long): String
    suspend fun isGoogleBackupSupported(walletId: Long): Boolean
    suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType>
    suspend fun getBestBackupType(walletId: Long, type: ImportAccountType): BackupAccountType?
    suspend fun getChain(chainId: ChainId): Chain

    suspend fun updateFavoriteChain(metaAccountId: Long, chainId: ChainId, isFavorite: Boolean)

    fun allMetaAccountsFlow(): StateFlow<List<MetaAccount>>
    fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount>
    suspend fun getSelectedLightMetaAccount(): LightMetaAccount
    suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount
    fun observeFavoriteChains(metaId: Long): Flow<Map<ChainId, Boolean>>

    fun observeNomisScores(): Flow<List<NomisScoreData>>
    fun observeNomisScore(metaId: Long): Flow<NomisScoreData?>
    suspend fun getSubstrateSecrets(metaId: Long) : EncodableStruct<SubstrateSecrets>?
    suspend fun getEthereumSecrets(metaId: Long) : EncodableStruct<EthereumSecrets>?
    suspend fun getTonSecrets(metaId: Long): EncodableStruct<TonSecrets>?
}
