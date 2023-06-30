package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AccountInteractor {
    suspend fun generateMnemonic(): List<String>

    fun getCryptoTypes(): List<CryptoType>

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ): Result<Unit>

    suspend fun createChainAccount(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ): Result<Unit>

    suspend fun importFromMnemonic(
        mnemonic: String,
        walletName: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        selectedEncryptionType: CryptoType,
        withEth: Boolean
    ): Result<Unit>

    suspend fun importChainAccountFromMnemonic(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ): Result<Unit>

    suspend fun importFromSeed(
        substrateSeed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?
    ): Result<Unit>

    suspend fun importChainFromSeed(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        seed: String,
        substrateDerivationPath: String,
        selectedEncryptionType: CryptoType
    ): Result<Unit>

    suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?
    ): Result<Unit>

    suspend fun importChainFromJson(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        json: String,
        password: String
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

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>?

    suspend fun getChainAccountSecrets(metaId: Long, chainId: ChainId): EncodableStruct<ChainAccountSecrets>?

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>

    suspend fun getChain(chainId: ChainId): Chain

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File>
}
