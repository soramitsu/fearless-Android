package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.astar

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar.AstarContributeSubmitter

@InstallIn(SingletonComponent::class)
@Module
class AstarContributionModule {

    @Provides
    fun provideAstarInteractor(
        accountRepository: AccountRepository
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
