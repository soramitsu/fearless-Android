package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.interlay

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.interlay.InterlayContributeSubmitter

@InstallIn(SingletonComponent::class)
@Module
class InterlayContributionModule {

    @Provides
    fun provideInterlayInteractor() = InterlayContributeInteractor()

    @Provides
    fun provideInterlaySubmitter(
        interactor: InterlayContributeInteractor
    ) = InterlayContributeSubmitter(interactor)

    @Provides
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
