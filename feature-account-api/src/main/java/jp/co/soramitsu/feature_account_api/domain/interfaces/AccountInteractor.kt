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

interface AccountInteractor {
    fun getSecuritySource(accountAddress: String): Single<SecuritySource>

    fun generateMnemonic(): Single<List<String>>

    fun getCryptoTypes(): Single<List<CryptoType>>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    ): Completable

    fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable

    fun importFromSeed(
        keyString: String,
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

    fun getAddressId(address: String): Single<ByteArray>

    fun isCodeSet(): Boolean

    fun savePin(code: String): Completable

    fun isPinCorrect(code: String): Single<Boolean>

    fun isBiometricEnabled(): Boolean

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun getAccount(address: String): Single<Account>

    fun observeSelectedAccount(): Observable<Account>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNode(): Single<Node>

    fun getSelectedNetworkType(): Single<Node.NetworkType>

    fun shouldOpenOnboarding(): Single<Boolean>

    fun observeGroupedAccounts(): Observable<List<Any>>

    fun selectAccount(address: String): Completable

    fun updateAccountName(account: Account, newName: String): Completable

    fun deleteAccount(address: String): Completable

    fun updateAccountPositionsInNetwork(newOrdering: List<Account>): Completable

    fun observeNodes(): Observable<List<Node>>

    fun observeSelectedNode(): Observable<Node>

    fun getNode(nodeId: Int): Single<Node>

    fun processAccountJson(json: String): Single<ImportJsonData>

    fun observeLanguages(): Observable<List<Language>>

    fun getSelectedLanguage(): Single<Language>

    fun changeSelectedLanguage(language: Language): Completable

    fun addNode(nodeName: String, nodeHost: String): Completable

    fun updateNode(nodeId: Int, newName: String, newHost: String): Completable

    fun getAccountsByNetworkTypeWithSelectedNode(networkType: Node.NetworkType): Single<Pair<List<Account>, Node>>

    fun selectNodeAndAccount(nodeId: Int, accountAddress: String): Completable

    fun selectNode(nodeId: Int): Completable

    fun deleteNode(nodeId: Int): Completable

    fun generateRestoreJson(accountAddress: String, password: String): Single<String>
}