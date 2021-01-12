package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource

class AccountAlreadyExistsException : Exception()

interface AccountRepository {

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getNodes(): Observable<List<Node>>

    fun getNode(nodeId: Int): Single<Node>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNode(): Single<Node>

    fun saveNode(node: Node): Completable

    fun removeNode(node: Node): Completable

    fun selectNode(node: Node): Completable

    fun getDefaultNode(networkType: Node.NetworkType): Single<Node>

    fun selectAccount(account: Account): Completable

    fun observeSelectedAccount(): Observable<Account>

    fun getSelectedAccount(): Single<Account>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun isAccountSelected(): Single<Boolean>

    fun removeAccount(account: Account): Completable

    fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    ): Completable

    fun observeAccounts(): Observable<List<Account>>

    fun getAccount(address: String): Single<Account>

    fun getMyAccounts(query: String, networkType: Node.NetworkType): Single<Set<String>>

    fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable

    fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable

    fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ): Completable

    fun isCodeSet(): Boolean

    fun savePinCode(code: String): Completable

    fun getPinCode(): String?

    fun isPinCorrect(code: String): Single<Boolean>

    fun generateMnemonic(): Single<List<String>>

    fun getAddressId(address: String): Single<ByteArray>

    fun isInCurrentNetwork(address: String): Single<Boolean>

    fun isBiometricEnabled(): Boolean

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun updateAccount(newAccount: Account): Completable

    fun observeNodes(): Observable<List<Node>>

    fun observeSelectedNode(): Observable<Node>

    fun updateAccounts(accounts: List<Account>): Completable

    fun deleteAccount(address: String): Completable

    fun processAccountJson(json: String): Single<ImportJsonData>

    fun observeLanguages(): Observable<List<Language>>

    fun getSelectedLanguage(): Single<Language>

    fun changeLanguage(language: Language): Completable

    fun getCurrentSecuritySource(): Single<SecuritySource>

    fun getSecuritySource(accountAddress: String): Single<SecuritySource>

    fun addNode(nodeName: String, nodeHost: String, networkType: Node.NetworkType): Completable

    fun updateNode(nodeId: Int, newName: String, newHost: String, networkType: Node.NetworkType): Completable

    fun checkNodeExists(nodeHost: String): Single<Boolean>

    fun getNetworkName(nodeHost: String): Single<String>

    fun getAccountsByNetworkType(networkType: Node.NetworkType): Single<List<Account>>

    fun deleteNode(nodeId: Int): Completable

    fun generateRestoreJson(account: Account, password: String): Single<String>
}