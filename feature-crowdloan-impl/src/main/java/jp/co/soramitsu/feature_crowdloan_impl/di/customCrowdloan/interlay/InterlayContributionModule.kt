package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.interlay

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay.InterlayContributeSubmitter

@Module
class InterlayContributionModule {

    @Provides
    @FeatureScope
    fun provideInterlayInteractor(
        accountRepository: AccountRepository,
    ) = InterlayContributeInteractor(accountRepository)

    @Provides
    @FeatureScope
    fun provideInterlaySubmitter(
        interactor: InterlayContributeInteractor
    ) = InterlayContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideInterlayFactory(
        submitter: InterlayContributeSubmitter,
        acalaInteractor: InterlayContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = InterlayContributeFactory(
        submitter,
        acalaInteractor,
        resourceManager
    )
}
