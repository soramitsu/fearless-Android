package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.model.UniversalWalletMigrationSnapshot
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AccountInteractor {
    suspend fun generateMnemonic(length: Mnemonic.Length): List<String>

    fun getCryptoTypes(): List<CryptoType>

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun createAccount(payload: AddAccountPayload): Result<Long>

    /** Import both roots from a decrypted legacy backup in one durable wallet creation. */
    suspend fun createAccountFromBackup(
        payload: AddAccountPayload.SubstrateOrEvm,
        ethereumPrivateKeyHex: String
    ): Result<Long>

    suspend fun importFromSeed(
        walletId: Long?,
        substrateSeed: String?,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    ): Result<Unit>

    fun validateJsonBackup(json: String, password: String)

    suspend fun importFromJson(
        walletId: Long?,
        json: String?,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ): Result<Unit>

    suspend fun isCodeSet(): Boolean

    suspend fun getPinHealth(): PinHealth

    suspend fun savePin(code: String)

    suspend fun isPinCorrect(code: String): Boolean

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

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
    suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount
    fun getMetaAccountsGoogleAddresses(): Flow<List<String>>

    suspend fun getChainAccountSecrets(metaId: Long, chainId: ChainId): EncodableStruct<ChainAccountSecrets>?

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>

    suspend fun googleBackupAddressForWallet(walletId: Long): String
    suspend fun isGoogleBackupSupported(walletId: Long): Boolean
    suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType>
    suspend fun getBestBackupType(walletId: Long, type: WalletEcosystem): BackupAccountType?

    suspend fun getChain(chainId: ChainId): Chain

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File>

    suspend fun updateAccountName(metaId: Long, name: String)

    suspend fun updateWalletBackedUp(metaId: Long)

    suspend fun updateWalletOnGoogleBackupDelete(metaId: Long)

    suspend fun updateFavoriteChain(chainId: ChainId, isFavorite: Boolean, metaId: Long)
    suspend fun selectedLightMetaAccount(): LightMetaAccount
    fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount>
    fun observeSelectedMetaAccountFavoriteChains(): Flow<Map<ChainId, Boolean>>
    fun universalWalletMigrationSnapshotFlow(): Flow<UniversalWalletMigrationSnapshot>
    suspend fun universalWalletMigrationSnapshot(): UniversalWalletMigrationSnapshot
    suspend fun isWalletRecoveryRequired(metaId: Long): Boolean
    fun walletRecoveryRequiredFlow(metaId: Long): Flow<Boolean>

    /** False when this build cannot sign in to Google Drive, so backup and import must use another way. */
    fun isGoogleBackupAvailable(): Boolean

    /**
     * Uploads the wallet's Google Drive backup. A stored phrase or seed that does not restore this
     * exact wallet is left out and the backup keeps the types that do; returns the types left out.
     */
    suspend fun saveGoogleBackupAccount(metaId: Long, googleBackupPassword: String): Set<BackupAccountType>
    suspend fun getGoogleBackupAccounts(): List<BackupAccountMeta>
    suspend fun getExtensionGoogleBackups(): List<BackupAccountMeta>

    /**
     * The wallet's app backups on Drive, newest first: the copies filed under its own address, one for
     * each device that backed it up. A backup the wallet was imported from is not among them unless it
     * restores this exact wallet's address.
     */
    suspend fun getGoogleBackupCopies(walletId: Long): List<BackupAccountMeta>

    /**
     * Deletes the confirmed [copies] of the wallet's backup that are filed under its own address. A
     * backup that is already gone from Drive counts as deleted; any other failure is thrown.
     */
    suspend fun deleteGoogleBackupAccount(walletId: Long, copies: List<BackupAccountMeta>)
    suspend fun authorizeGoogleBackup(launcher: ActivityResultLauncher<Intent>): Boolean
    suspend fun getSubstrateSecrets(metaId: Long): EncodableStruct<SubstrateSecrets>?

    fun getMnemonic(metaId: Long): Flow<Mnemonic>
    fun getSeedForSeedExport(metaId: Long): Flow<ComponentHolder>
    fun getDerivationPathForExport(metaId: Long): Flow<ComponentHolder>
    suspend fun saveChainSelectFilter(metaId: Long, filterValue: String)
}
