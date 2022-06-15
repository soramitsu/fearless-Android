package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.CollatorDetailsViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.ValidatorDetailsViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class CollatorDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(CollatorDetailsViewModel::class)
    fun provideCollatorViewModel(
        interactor: StakingInteractor,
        stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
        router: StakingRouter,
        collator: CollatorDetailsParcelModel,
        addressIconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager,
        chainRegistry: ChainRegistry
    ): ViewModel {
        return CollatorDetailsViewModel(
            interactor,
            stakingParachainScenarioInteractor,
            router,
            collator,
            addressIconGenerator,
            externalAccountActions,
            appLinksProvider,
            resourceManager,
            chainRegistry
        )
    }

    @Provides
    fun provideCollatorViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CollatorDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CollatorDetailsViewModel::class.java)
    }
}

@Module(includes = [ViewModelModule::class])
class ValidatorDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ValidatorDetailsViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        router: StakingRouter,
        validator: ValidatorDetailsParcelModel,
        addressIconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager,
        chainRegistry: ChainRegistry
    ): ViewModel {
        return ValidatorDetailsViewModel(
            interactor,
            stakingRelayChainScenarioInteractor,
            router,
            validator,
            addressIconGenerator,
            externalAccountActions,
            appLinksProvider,
            resourceManager,
            chainRegistry
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ValidatorDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ValidatorDetailsViewModel::class.java)
    }
}
