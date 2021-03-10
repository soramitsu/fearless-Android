package jp.co.soramitsu.feature_account_api.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions

interface AccountFeatureApi {

    fun provideAccountRepository(): AccountRepository

    fun externalAccountActions(): ExternalAccountActions.Presentation
}