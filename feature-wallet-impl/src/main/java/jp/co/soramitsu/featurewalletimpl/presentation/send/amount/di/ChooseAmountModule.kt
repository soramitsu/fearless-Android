package jp.co.soramitsu.featurewalletimpl.presentation.send.amount.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletInteractor
import jp.co.soramitsu.featurewalletimpl.presentation.send.phishing.warning.api.PhishingWarningMixin
import jp.co.soramitsu.featurewalletimpl.presentation.send.phishing.warning.impl.PhishingWarningProvider

@InstallIn(SingletonComponent::class)
@Module
class ChooseAmountModule {

    @Provides
    fun providePhishingAddressMixin(interactor: WalletInteractor): PhishingWarningMixin {
        return PhishingWarningProvider(interactor)
    }
}
