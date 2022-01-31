package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.ConfirmContributeViewModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ConfirmContributeModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmContributeViewModel::class)
    fun provideViewModel(
        interactor: CrowdloanContributeInteractor,
        router: CrowdloanRouter,
        resourceManager: ResourceManager,
        chainRegistry: ChainRegistry,
        assetUseCase: AssetUseCase,
        validationExecutor: ValidationExecutor,
        payload: ConfirmContributePayload,
        accountUseCase: SelectedAccountUseCase,
        addressIconGenerator: AddressIconGenerator,
        validationSystem: ContributeValidationSystem,
        externalAccountActions: ExternalAccountActions.Presentation,
        customContributeManager: CustomContributeManager,
        transferValidityChecks: TransferValidityChecks.Presentation,
    ): ViewModel {
        return ConfirmContributeViewModel(
            router,
            interactor,
            resourceManager,
            chainRegistry,
            assetUseCase,
            accountUseCase,
            addressIconGenerator,
            validationExecutor,
            payload,
            validationSystem,
            customContributeManager,
            externalAccountActions,
            transferValidityChecks,
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmContributeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmContributeViewModel::class.java)
    }
}
