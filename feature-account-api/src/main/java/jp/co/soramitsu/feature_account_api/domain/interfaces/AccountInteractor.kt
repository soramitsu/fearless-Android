package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import kotlinx.coroutines.flow.Flow

interface AccountInteractor {
    suspend fun getSecuritySource(accountAddress: String): SecuritySource

    suspend fun generateMnemonic(): List<String>

    fun getCryptoTypes(): List<CryptoType>

    suspend fun getPreferredCryptoType(): CryptoType

    suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    ): Result<Unit>

    suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Result<Unit>

    suspend fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Result<Unit>

    suspend fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ): Result<Unit>

    suspend fun isCodeSet(): Boolean

    suspend fun savePin(code: String)

    suspend fun isPinCorrect(code: String): Boolean

    suspend fun isBiometricEnabled(): Boolean

    suspend fun setBiometricOn()

    suspend fun setBiometricOff()

    suspend fun getAccount(address: String): Account

    fun selectedAccountFlow(): Flow<Account>

    suspend fun getSelectedAccount(): Account

    suspend fun getNetworks(): List<Network>

    suspend fun getSelectedNode(): Node

    fun groupedAccountsFlow(): Flow<List<Any>>

    suspend fun selectAccount(address: String)

    suspend fun updateAccountName(account: Account, newName: String)

    suspend fun deleteAccount(address: String)

    suspend fun updateAccountPositionsInNetwork(newOrdering: List<Account>)

    fun nodesFlow(): Flow<List<Node>>

    fun selectedNodeFlow(): Flow<Node>

    suspend fun getNode(nodeId: Int): Node

    suspend fun processAccountJson(json: String): Result<ImportJsonData>

    fun getLanguages(): List<Language>

    suspend fun getSelectedLanguage(): Language

    suspend fun changeSelectedLanguage(language: Language)

    suspend fun addNode(nodeName: String, nodeHost: String): Result<Unit>

    suspend fun updateNode(nodeId: Int, newName: String, newHost: String): Result<Unit>

    suspend fun getAccountsByNetworkTypeWithSelectedNode(networkType: Node.NetworkType): Pair<List<Account>, Node>

    suspend fun selectNodeAndAccount(nodeId: Int, accountAddress: String)

    suspend fun selectNode(nodeId: Int)

    suspend fun deleteNode(nodeId: Int)

    suspend fun generateRestoreJson(accountAddress: String, password: String): Result<String>
}