package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.WalletRecoveryStatus
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
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

    /**
     * Null means selection is absent or its current catalog mapping is not ready.
     * Every local selection replacement revokes the preceding account before mapping.
     * Callers must keep null distinct from a completed empty wallet and clear on failure.
     */
    fun selectedMetaAccountStateFlow(): Flow<MetaAccount?>

    /** Cold per-wallet state; null revokes removed or not-yet-mapped accounts. No equality conflation. */
    fun metaAccountStateFlow(metaId: Long): Flow<MetaAccount?>

    suspend fun findMetaAccount(accountId: ByteArray): MetaAccount?

    suspend fun allMetaAccounts(): List<MetaAccount>

    fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>>
    fun lightMetaAccountFlow(metaId: Long): Flow<LightMetaAccount>
    suspend fun selectMetaAccount(metaId: Long)

    suspend fun updateMetaAccountName(metaId: Long, newName: String)

    suspend fun updateMetaAccountBackedUp(metaId: Long)

    suspend fun updateWalletOnGoogleBackupDelete(metaId: Long)

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun isAccountSelected(): Boolean

    suspend fun createAccount(payload: AddAccountPayload): Long

    /** Preserve a backed-up independent EVM key with the Substrate mnemonic atomically. */
    suspend fun createAccountFromBackup(
        payload: AddAccountPayload.SubstrateOrEvm,
        ethereumPrivateKeyHex: String
    ): Long

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

    suspend fun getPinHealth(): PinHealth

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

    /**
     * The signing keypair, plus the entropy, seed and path only when they
     * re-derive it. Drifted recovery material is withheld so that it can never
     * be exported or backed up as a phrase that restores another account.
     */
    suspend fun getChainAccountSecrets(metaId: Long?, chainId: ChainId): EncodableStruct<ChainAccountSecrets>?

    suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String): String

    suspend fun isAccountExists(accountId: AccountId): Boolean

    suspend fun isInCurrentNetwork(address: String, chainId: ChainId): Boolean

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>
    fun googleAddressAllWalletsFlow(): Flow<List<String>>
    suspend fun googleBackupAddressForWallet(walletId: Long): String
    suspend fun isGoogleBackupSupported(walletId: Long): Boolean
    suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType>
    suspend fun getBestBackupType(walletId: Long, type: WalletEcosystem): BackupAccountType?
    suspend fun getChain(chainId: ChainId): Chain

    suspend fun updateFavoriteChain(metaAccountId: Long, chainId: ChainId, isFavorite: Boolean)

    fun allMetaAccountsFlow(): StateFlow<List<MetaAccount>>
    fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount>
    suspend fun getSelectedLightMetaAccount(): LightMetaAccount
    suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount
    fun observeFavoriteChains(metaId: Long): Flow<Map<ChainId, Boolean>>

    fun observeNomisScores(): Flow<List<NomisScoreData>>
    fun observeNomisScore(metaId: Long): Flow<NomisScoreData?>
    /** Same recovery-material contract as [getChainAccountSecrets]. */
    suspend fun getSubstrateSecrets(metaId: Long) : EncodableStruct<SubstrateSecrets>?
    /** Same recovery-material contract as [getChainAccountSecrets]. */
    suspend fun getEthereumSecrets(metaId: Long) : EncodableStruct<EthereumSecrets>?
    /**
     * The stored TON phrase exactly as typed, spaces and capitals included,
     * whenever it re-derives the keypair; null when it re-derives nothing.
     * The TON key keeps signing through the keypair provider either way.
     */
    suspend fun getTonSecrets(metaId: Long): EncodableStruct<TonSecrets>?

    /**
     * Whether any key of this wallet needs recovery (a quarantine or an
     * identity marker). For the wallet banner and portable export only;
     * signing callers use [isSigningRecoveryRequired] for the key they sign
     * with, so one broken key never blocks the others.
     */
    suspend fun isWalletRecoveryRequired(metaId: Long): Boolean
    fun walletRecoveryRequiredFlow(metaId: Long): Flow<Boolean>

    /** Every item of this wallet that needs attention, key by key. */
    suspend fun walletRecoveryStatus(metaId: Long): WalletRecoveryStatus
    fun walletRecoveryStatusFlow(metaId: Long): Flow<WalletRecoveryStatus>

    /** Whether the key for [scope] cannot sign. Other scopes are unaffected. */
    suspend fun isSigningRecoveryRequired(metaId: Long, scope: WalletSecretScope): Boolean

    /**
     * Whether the key this wallet signs with on [chain] cannot sign: the
     * per-network key when the wallet has one for that chain, otherwise the
     * root key of the chain's ecosystem.
     */
    suspend fun isSigningRecoveryRequired(metaId: Long, chain: IChain): Boolean

    /**
     * Whether the stored phrase, seed and path of the root key of [ecosystem]
     * re-derive it. Null when the key is absent or its scope is blocked.
     */
    suspend fun recoveryAssessment(metaId: Long, ecosystem: WalletEcosystem): RecoveryAssessment?
}
