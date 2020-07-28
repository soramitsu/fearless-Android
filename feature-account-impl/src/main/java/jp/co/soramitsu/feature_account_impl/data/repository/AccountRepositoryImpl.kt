package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasource

class AccountRepositoryImpl(
    private val accountDatasource: AccountDatasource,
    private val appLinksProvider: AppLinksProvider
) : AccountRepository {

    override fun getTermsAddress(): Single<String> {
        return Single.just(appLinksProvider.termsUrl)
    }

    override fun getPrivacyAddress(): Single<String> {
        return Single.just(appLinksProvider.privacyUrl)
    }
}