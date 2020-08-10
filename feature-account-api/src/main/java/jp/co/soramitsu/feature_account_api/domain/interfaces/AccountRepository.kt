package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

interface AccountRepository {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>

    fun getEncryptionTypes(): Single<List<CryptoType>>

    fun getSelectedEncryptionType(): Single<CryptoType>

    fun saveSelectedEncryptionType(encryptionType: CryptoType): Completable

    fun getNetworks(): Single<List<Network>>

    fun getSelectedNetwork(): Single<NetworkType>

    fun saveSelectedNetwork(networkType: NetworkType): Completable
}