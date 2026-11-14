package jp.co.soramitsu.account.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault
import jp.co.soramitsu.account.impl.data.biometric.KeystoreBiometricPinVault

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricModule {
    @Binds
    abstract fun biometricPinVault(vault: KeystoreBiometricPinVault): BiometricPinVault
}
