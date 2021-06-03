package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.CrowdloanContributeViewModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin

@Module(includes = [ViewModelModule::class])
class CrowdloanContributeModule {

    @Provides
    @IntoMap
    @ViewModelKey(CrowdloanContributeViewModel::class)
    fun provideViewModel(
        interactor: CrowdloanContributeInteractor,
        router: CrowdloanRouter,
        resourceManager: ResourceManager,
        assetUseCase: AssetUseCase,
        validationExecutor: ValidationExecutor,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        payload: ContributePayload,
        validationSystem: ContributeValidationSystem,
        customContributeManager: CustomContributeManager,
    ): ViewModel {
        return CrowdloanContributeViewModel(
            router,
            interactor,
            resourceManager,
            assetUseCase,
            validationExecutor,
            feeLoaderMixin,
            payload,
            validationSystem,
            customContributeManager
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CrowdloanContributeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CrowdloanContributeViewModel::class.java)
    }
}
