package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import kotlinx.coroutines.flow.Flow

class AccountAlreadyExistsException : Exception()

interface AccountRepository {

    fun getEncryptionTypes(): List<CryptoType>

    suspend fun getNode(nodeId: Int): Node

    suspend fun getNetworks(): List<Network>

    suspend fun getSelectedNode(): Node

    suspend fun selectNode(node: Node)

    suspend fun getDefaultNode(networkType: Node.NetworkType): Node

    suspend fun selectAccount(account: Account)

    fun selectedAccountFlow(): Flow<Account>

    suspend fun getSelectedAccount(): Account

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun isAccountSelected(): Boolean

    suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    )

    fun accountsFlow(): Flow<List<Account>>

    suspend fun getAccounts(): List<Account>

    suspend fun getAccount(address: String): Account

    suspend fun getMyAccounts(query: String, networkType: Node.NetworkType): Set<Account>

    suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    )

    suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    )

    suspend fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    )

    suspend fun isCodeSet(): Boolean

    suspend fun savePinCode(code: String)

    suspend fun getPinCode(): String?

    suspend fun generateMnemonic(): List<String>

    suspend fun isInCurrentNetwork(address: String): Boolean

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

    suspend fun updateAccount(newAccount: Account)

    fun nodesFlow(): Flow<List<Node>>

    fun selectedNodeFlow(): Flow<Node>

    fun selectedNetworkTypeFlow(): Flow<Node.NetworkType>

    suspend fun updateAccounts(accounts: List<Account>)

    suspend fun deleteAccount(address: String)

    suspend fun processAccountJson(json: String): ImportJsonData

    fun getLanguages(): List<Language>

    suspend fun selectedLanguage(): Language

    suspend fun changeLanguage(language: Language)

    suspend fun getCurrentSecuritySource(): SecuritySource

    suspend fun getSecuritySource(accountAddress: String): SecuritySource

    suspend fun addNode(nodeName: String, nodeHost: String, networkType: Node.NetworkType)

    suspend fun updateNode(nodeId: Int, newName: String, newHost: String, networkType: Node.NetworkType)

    suspend fun checkNodeExists(nodeHost: String): Boolean

    /**
     * @throws FearlessException
     */
    suspend fun getNetworkName(nodeHost: String): String

    suspend fun getAccountsByNetworkType(networkType: Node.NetworkType): List<Account>

    suspend fun deleteNode(nodeId: Int)

    fun createQrAccountContent(account: Account): String

    suspend fun generateRestoreJson(account: Account, password: String): String

    suspend fun isAccountExists(accountAddress: String): Boolean
}
