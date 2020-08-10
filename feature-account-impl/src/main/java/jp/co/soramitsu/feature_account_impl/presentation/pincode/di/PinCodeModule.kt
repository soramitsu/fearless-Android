package jp.co.soramitsu.feature_main_impl.presentation.pincode.di

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintCallback
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintWrapper

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PinCodeModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(PinCodeViewModel::class)
    fun provideViewModel(interactor: PinCodeInteractor, mainRouter: MainRouter, progress: WithProgress, maxPinCodeLength: Int): ViewModel {
        return PinCodeViewModel(interactor, mainRouter, progress, maxPinCodeLength)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PinCodeViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PinCodeViewModel::class.java)
    }

    @Provides
    fun provideFingerprintWrapper(fragment: Fragment, contextManager: ContextManager, resourceManager: ResourceManager, fingerprintListener: FingerprintCallback): FingerprintWrapper {
        val biometricManager = BiometricManager.from(contextManager.getContext())
        val biometricPrompt = BiometricPrompt(fragment, MainThreadExecutor(), fingerprintListener)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(resourceManager.getString(R.string.biometric_dialog_title))
            .setNegativeButtonText(resourceManager.getString(R.string.common_cancel))
            .build()

        return FingerprintWrapper(
            biometricManager,
            biometricPrompt,
            promptInfo
        )
    }

    @Provides
    fun provideFingerprintListener(pinCodeViewModel: PinCodeViewModel): FingerprintCallback {
        return FingerprintCallback(pinCodeViewModel)
    }
}