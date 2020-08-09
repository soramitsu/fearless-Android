package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {

    override fun getEncryptionTypesWithSelected(): Single<Pair<List<CryptoType>, CryptoType>> {
        return accountRepository.getEncryptionTypes()
            .flatMap { encryptionTypes ->
                accountRepository.getSelectedEncryptionType()
                    .map { Pair(encryptionTypes, it) }
            }
    }

    override fun saveSelectedEncryptionType(encryptionType: CryptoType): Completable {
        return accountRepository.saveSelectedEncryptionType(encryptionType)
    }
}