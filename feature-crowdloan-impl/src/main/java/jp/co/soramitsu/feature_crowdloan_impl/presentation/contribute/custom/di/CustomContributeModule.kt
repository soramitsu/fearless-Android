package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload

@Module(includes = [ViewModelModule::class])
class CustomContributeModule {

    @Provides
    @IntoMap
    @ViewModelKey(CustomContributeViewModel::class)
    fun provideViewModel(
        customContributeManager: CustomContributeManager,
        payload: CustomContributePayload,
        router: CrowdloanRouter,
    ): ViewModel {
        return CustomContributeViewModel(customContributeManager, payload, router)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CustomContributeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CustomContributeViewModel::class.java)
    }
}
