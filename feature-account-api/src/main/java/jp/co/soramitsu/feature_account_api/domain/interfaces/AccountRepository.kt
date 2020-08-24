package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getSelectedEncryptionType(): Single<CryptoType>

    fun selectEncryptionType(cryptoType: CryptoType): Completable

    fun getNodes(): Single<List<Node>>

    fun getSelectedNode(): Single<Node>

    fun saveNode(node: Node): Completable

    fun removeNode(node: Node): Completable

    fun selectNode(node: Node): Completable

    fun selectAccount(account: Account): Completable

    fun getSelectedAccount(): Single<Account>

    fun removeAccount(account: Account): Completable

    fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable

    fun getAccounts(): Single<List<Account>>

    fun getSourceTypes(): Single<List<SourceType>>

    fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromJson(json: String, password: String, networkType: NetworkType): Completable

    fun isCodeSet(): Single<Boolean>

    fun savePinCode(code: String): Completable

    fun getPinCode(): String?

    fun isPinCorrect(code: String): Single<Boolean>

    fun generateMnemonic(): Single<List<String>>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable
}