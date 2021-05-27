package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura.KaruraContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura.KaruraContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeViewState
import javax.inject.Provider

@Module(
    includes = [
        KaruraContributionModule::class
    ]
)
class CustomContributeModule {

    @Provides
    @FeatureScope
    fun provideCustomContributionManager(
        factories: @JvmSuppressWildcards Set<CustomContributeFactory>,
        accountRepository: AccountRepository
    ) = CustomContributeManager(factories, accountRepository)
}
