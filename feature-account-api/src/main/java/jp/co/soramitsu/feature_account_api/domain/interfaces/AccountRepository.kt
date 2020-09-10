package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_api.domain.model.User

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getNodes(): Observable<List<Node>>

    fun getSelectedNode(): Single<Node>

    fun saveNode(node: Node): Completable

    fun removeNode(node: Node): Completable

    fun selectNode(node: Node): Completable

    fun selectAccount(account: User): Completable

    fun getSelectedAccount(): Single<User>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun isAccountSelected(): Single<Boolean>

    fun removeAccount(account: User): Completable

    fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
    ): Completable

    fun getAccounts(): Single<List<User>>

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

    fun getAddressId(): Single<ByteArray>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable
}