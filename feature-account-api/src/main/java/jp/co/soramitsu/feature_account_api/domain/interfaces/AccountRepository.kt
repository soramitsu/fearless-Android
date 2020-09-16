package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

class AccountAlreadyExistsException : Exception()

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getNodes(): Observable<List<Node>>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNode(): Single<Node>

    fun saveNode(node: Node): Completable

    fun removeNode(node: Node): Completable

    fun selectNode(node: Node): Completable

    fun getDefaultNode(networkType: Node.NetworkType): Single<Node>

    fun selectAccount(account: Account): Completable

    fun observeSelectedAccount(): Observable<Account>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun isAccountSelected(): Single<Boolean>

    fun removeAccount(account: Account): Completable

    fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
    ): Completable

    fun observeAccounts(): Observable<List<Account>>

    fun getAccount(address: String): Single<Account>

    fun getSourceTypes(): Single<List<SourceType>>

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

    fun importFromJson(json: String, password: String, networkType: Node.NetworkType): Completable

    fun isCodeSet(): Single<Boolean>

    fun savePinCode(code: String): Completable

    fun getPinCode(): String?

    fun isPinCorrect(code: String): Single<Boolean>

    fun generateMnemonic(): Single<List<String>>

    fun getAddressId(account: Account): Single<ByteArray>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun updateAccount(newAccount: Account): Completable

    fun updateAccounts(accounts: List<Account>) : Completable

    fun deleteAccount(address: String): Completable
}