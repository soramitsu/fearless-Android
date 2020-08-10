package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {

    override fun getMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            mutableListOf<String>().apply {
                add("song")
                add("toss")
                add("odor")
                add("click")
                add("blouse")
                add("lesson")
                add("runway")
                add("popular")
                add("owner")
                add("caught")
                add("wrist")
                add("poverty")
            }
        }
    }

    override fun getEncryptionTypesWithSelected(): Single<Pair<List<CryptoType>, CryptoType>> {
        return accountRepository.getEncryptionTypes()
            .flatMap { encryptionTypes ->
                accountRepository.getSelectedEncryptionType()
                    .map { Pair(encryptionTypes, it) }
            }
    }

    override fun getNetworksWithSelected(): Single<Pair<List<Network>, NetworkType>> {
        return accountRepository.getNetworks()
            .flatMap { networks ->
                accountRepository.getSelectedNetwork()
                    .map { Pair(networks, it) }
            }
    }

    override fun createAccount(accountName: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable {
        return accountRepository.createAccount(accountName, encryptionType, derivationPath, networkType)
    }
}