package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.astar

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarContributeSubmitter

@InstallIn(SingletonComponent::class)
@Module
class AstarContributionModule {

    @Provides
    fun provideAstarInteractor(
        accountRepository: AccountRepository,
    ) = AstarContributeInteractor(accountRepository)

    @Provides
    fun provideAstarSubmitter(
        interactor: AstarContributeInteractor
    ) = AstarContributeSubmitter(interactor)

    @Provides
    @IntoSet
    fun provideAstarFactory(
        submitter: AstarContributeSubmitter,
        acalaInteractor: AstarContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = AstarContributeFactory(
        submitter,
        acalaInteractor,
        resourceManager
    )
}
