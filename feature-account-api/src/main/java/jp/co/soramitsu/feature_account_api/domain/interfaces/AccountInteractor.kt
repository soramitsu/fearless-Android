package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

interface AccountInteractor {

    fun getMnemonic(): Single<List<String>>

    fun getEncryptionTypesWithSelected(): Single<Pair<List<CryptoType>, CryptoType>>

    fun getNetworksWithSelected(): Single<Pair<List<Network>, NetworkType>>

    fun createAccount(accountName: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable
}