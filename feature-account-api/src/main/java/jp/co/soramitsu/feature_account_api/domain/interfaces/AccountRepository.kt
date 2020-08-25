package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getSelectedEncryptionType(): Single<CryptoType>

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNetwork(): Single<NetworkType>

    fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable

    fun getSourceTypes(): Single<List<SourceType>>

    fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromJson(json: String, password: String, networkType: NetworkType): Completable

    fun isCodeSet(): Single<Boolean>

    fun savePinCode(code: String): Completable

    fun getPinCode(): String?

    fun isPinCorrect(code: String): Single<Boolean>

    fun generateMnemonic(): Single<List<String>>

    fun getAddressId(): Single<ByteArray>

    fun getAddress(): Single<String>

    fun getUsername(): Single<String>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun getExistingAccountName(): String?
}