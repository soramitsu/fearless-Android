package jp.co.soramitsu.feature_account_impl.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor