package jp.co.soramitsu.soracard.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.impl.domain.SoraCardInteractorImpl
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface SoraCardFeatureBindModule {
    @Binds
    @Singleton
    fun bindsSoraCardInteractor(soraCardInteractor: SoraCardInteractorImpl): SoraCardInteractor
}
