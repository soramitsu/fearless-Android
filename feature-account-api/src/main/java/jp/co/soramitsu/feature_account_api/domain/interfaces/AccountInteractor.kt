package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_api.domain.model.Account

interface AccountInteractor {

    fun getSelectedNetworkName(): Single<String>

    fun getMnemonic(): Single<List<String>>

    fun getSourceTypesWithSelected(): Single<Pair<List<SourceType>, SourceType>>

    fun getCryptoTypes(): Single<List<CryptoType>>

    fun getPreferredCryptoType(): Single<CryptoType>

    fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
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

    fun importFromJson(json: String, password: String, node: Node.NetworkType): Completable

    fun getAddressId(): Single<ByteArray>

    fun getSelectedLanguage(): Single<String>

    fun isCodeSet(): Single<Boolean>

    fun savePin(code: String): Completable

    fun isPinCorrect(code: String): Single<Boolean>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun getSelectedAccount(): Single<Account>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNode(): Single<Node>

    fun getSelectedNetwork(): Single<Network>

    fun shouldOpenOnboarding(): Single<Boolean>
}