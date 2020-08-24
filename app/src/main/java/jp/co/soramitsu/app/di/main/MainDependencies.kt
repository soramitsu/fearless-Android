package jp.co.soramitsu.app.di.main

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface MainDependencies {

    fun accountRepository(): AccountRepository
}