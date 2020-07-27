package jp.co.soramitsu.feature_account_impl.data.repository

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasource

class AccountRepositoryImpl(
    private val accountDatasource: AccountDatasource
): AccountRepository {
}