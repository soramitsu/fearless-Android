package jp.co.soramitsu.account.api.domain.interfaces

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.flow.Flow

interface AccountInteractor {
    suspend fun generateMnemonic(length: Mnemonic.Length): List<String>

    fun getCryptoTypes(): List<CryptoType>

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun createAccount(payload: AddAccountPayload): Result<Long>

    suspend fun importFromSeed(
        substrateSeed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    ): Result<Unit>

    fun validateJsonBackup(json: String, password: String)

    suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ): Result<Unit>

    suspend fun isCodeSet(): Boolean

    suspend fun savePin(code: String)

    suspend fun isPinCorrect(code: String): Boolean

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

    suspend fun getAccount(address: String): Account

    fun selectedAccountFlow(): Flow<Account>

    fun selectedMetaAccountFlow(): Flow<MetaAccount>

    suspend fun selectedMetaAccount(): MetaAccount

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>

    suspend fun selectMetaAccount(metaId: Long)

    suspend fun deleteAccount(metaId: Long)

    suspend fun updateAccountPositionsInNetwork(idsInNewOrder: List<Long>)

    suspend fun processAccountJson(json: String): Result<ImportJsonData>

    fun getLanguages(): List<Language>

    suspend fun getSelectedLanguage(): Language

    suspend fun changeSelectedLanguage(language: Language)

    suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String): Result<String>

    suspend fun getMetaAccount(metaId: Long): MetaAccount
    fun getMetaAccountsGoogleAddresses(): Flow<List<String>>

    suspend fun getChainAccountSecrets(metaId: Long, chainId: ChainId): EncodableStruct<ChainAccountSecrets>?

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>

    suspend fun googleBackupAddressForWallet(walletId: Long): String
    suspend fun isGoogleBackupSupported(walletId: Long): Boolean
    suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType>

    suspend fun getChain(chainId: ChainId): Chain

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File>

    suspend fun updateAccountName(metaId: Long, name: String)

    suspend fun updateWalletBackedUp(metaId: Long)

    suspend fun updateWalletOnGoogleBackupDelete(metaId: Long)

    suspend fun updateFavoriteChain(chainId: ChainId, isFavorite: Boolean, metaId: Long)
    suspend fun selectedLightMetaAccount(): LightMetaAccount
    fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount>
    fun observeSelectedMetaAccountFavoriteChains(): Flow<Map<ChainId, Boolean>>

    suspend fun saveGoogleBackupAccount(metaId: Long, googleBackupPassword: Int)
    suspend fun getGoogleBackupAccounts(): List<BackupAccountMeta>
    suspend fun getExtensionGoogleBackups(): List<BackupAccountMeta>
    suspend fun deleteGoogleBackupAccount(walletId: Long, address: String)
    suspend fun authorizeGoogleBackup(launcher: ActivityResultLauncher<Intent>): Boolean
    suspend fun getSubstrateSecrets(metaId: Long): EncodableStruct<SubstrateSecrets>?

    fun getMnemonic(metaId: Long): Flow<Mnemonic>
    fun getSeedForSeedExport(metaId: Long): Flow<ComponentHolder>
    fun getDerivationPathForMnemonicExport(metaId: Long): Flow<ComponentHolder>
}
