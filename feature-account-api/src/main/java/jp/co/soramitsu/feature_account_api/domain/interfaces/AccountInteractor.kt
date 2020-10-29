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
        node: Node
    ): Completable

    fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable

    fun importFromJson(json: String, password: String, name: String, node: Node): Completable

    fun getAddressId(account: Account): Single<ByteArray>

    fun isCodeSet(): Single<Boolean>

    fun savePin(code: String): Completable

    fun isPinCorrect(code: String): Single<Boolean>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun getAccount(address: String): Single<Account>

    fun observeSelectedAccount(): Observable<Account>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNode(): Single<Node>

    fun getSelectedNetwork(): Single<Network>

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

    fun getAccountsByNetworkType(networkType: Node.NetworkType): Single<List<Account>>

    fun selectNodeAndAccount(nodeId: Int, accountAddress: String): Completable

    fun getNetwork(networkType: Node.NetworkType): Single<Network>

    fun deleteNode(nodeId: Int): Completable
}