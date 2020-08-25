package jp.co.soramitsu.app.main.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface MainDependencies {

    fun accountRepository(): AccountRepository
}