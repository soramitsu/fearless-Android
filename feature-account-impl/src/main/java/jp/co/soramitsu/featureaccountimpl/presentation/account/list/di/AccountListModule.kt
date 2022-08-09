package jp.co.soramitsu.featureaccountimpl.presentation.account.list.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountInteractor
import jp.co.soramitsu.featureaccountimpl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.featureaccountimpl.presentation.account.mixin.impl.AccountListingProvider

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class AccountListModule {

    @Provides
    fun provideAccountListingMixin(
        interactor: AccountInteractor,
        addressIconGenerator: AddressIconGenerator
    ): AccountListingMixin = AccountListingProvider(interactor, addressIconGenerator)
}
