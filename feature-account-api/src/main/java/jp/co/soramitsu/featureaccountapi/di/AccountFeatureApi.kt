package jp.co.soramitsu.featureaccountapi.di

import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.featureaccountapi.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featureaccountapi.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.featureaccountapi.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.featureaccountapi.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalAccountActions

interface AccountFeatureApi {

    fun provideAccountRepository(): AccountRepository

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun accountUpdateScope(): AccountUpdateScope

    fun addressDisplayUseCase(): AddressDisplayUseCase

    fun accountUseCase(): SelectedAccountUseCase

    fun extrinsicService(): ExtrinsicService

    fun shouldShowEducationalStoriesUseCase(): ShouldShowEducationalStoriesUseCase

    fun getEducationalStoriesUseCase(): GetEducationalStoriesUseCase
}
