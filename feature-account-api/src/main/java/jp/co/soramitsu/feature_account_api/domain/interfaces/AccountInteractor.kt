package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

interface AccountInteractor {

    fun getMnemonic(): Single<List<String>>

    fun getSourceTypesWithSelected(): Single<Pair<List<SourceType>, SourceType>>

    fun getEncryptionTypesWithSelected(): Single<Pair<List<CryptoType>, CryptoType>>

    fun getNetworksWithSelected(): Single<Pair<List<Network>, NetworkType>>

    fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable

    fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable

    fun importFromJson(json: String, password: String, node: NetworkType): Completable

    fun isCodeSet(): Single<Boolean>

    fun savePin(code: String): Completable

    fun isPinCorrect(code: String): Single<Boolean>

    fun isBiometricEnabled(): Single<Boolean>

    fun setBiometricOn(): Completable

    fun setBiometricOff(): Completable

    fun accountExists(): Single<Boolean>
}