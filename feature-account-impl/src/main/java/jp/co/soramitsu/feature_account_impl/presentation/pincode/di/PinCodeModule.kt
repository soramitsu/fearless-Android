package jp.co.soramitsu.feature_account_impl.presentation.pincode.di

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintCallback
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PinCodeModule {

    @Provides
    @IntoMap
    @ViewModelKey(PinCodeViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        deviceVibrator: DeviceVibrator,
        resourceManager: ResourceManager,
        pinCodeAction: PinCodeAction
    ): ViewModel {
        return PinCodeViewModel(
            interactor,
            router,
            deviceVibrator,
            resourceManager,
            pinCodeAction
        )
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PinCodeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(PinCodeViewModel::class.java)
    }

    @Provides
    fun provideFingerprintWrapper(fragment: Fragment, context: Context, resourceManager: ResourceManager, fingerprintListener: FingerprintCallback): FingerprintWrapper {
        val biometricManager = BiometricManager.from(context)
        val biometricPrompt = BiometricPrompt(fragment, MainThreadExecutor(), fingerprintListener)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(resourceManager.getString(R.string.pincode_biometry_dialog_title))
            .setNegativeButtonText(resourceManager.getString(R.string.common_cancel))
            .build()

        return FingerprintWrapper(biometricManager, biometricPrompt, promptInfo)
    }

    @Provides
    fun provideFingerprintListener(pinCodeViewModel: PinCodeViewModel): FingerprintCallback {
        return FingerprintCallback(pinCodeViewModel)
    }
}