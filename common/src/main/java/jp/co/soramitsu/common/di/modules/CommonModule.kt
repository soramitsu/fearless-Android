package jp.co.soramitsu.common.di.modules

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Vibrator
import coil.ImageLoader
import coil.decode.SvgDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.CachingAddressIconGenerator
import jp.co.soramitsu.common.address.StatelessAddressIconGenerator
import jp.co.soramitsu.common.data.FileProviderImpl
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.impl.NetworkStateProvider
import jp.co.soramitsu.common.mixin.impl.UpdatesProvider
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.resources.ResourceManagerImpl
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import java.security.SecureRandom
import java.util.Random
import javax.inject.Qualifier
import javax.inject.Singleton

const val SHARED_PREFERENCES_FILE = "fearless_prefs"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Caching

@InstallIn(SingletonComponent::class)
@Module
class CommonModule {

    @Provides
    @Singleton
    fun provideComputationalCache() = ComputationalCache()

    @Provides
    @Singleton
    fun imageLoader(context: Context) = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .build()

    @Provides
    @Singleton
    fun provideResourceManager(contextManager: ContextManager): ResourceManager {
        return ResourceManagerImpl(contextManager)
    }

    @Provides
    @Singleton
    fun provideContextManager(context: Context): ContextManager {
        return ContextManager.getInstanceOrInit(context, LanguagesHolder())
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providePreferences(sharedPreferences: SharedPreferences): Preferences {
        return PreferencesImpl(sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideEncryptionUtil(context: Context): EncryptionUtil {
        return EncryptionUtil(context)
    }

    @Provides
    @Singleton
    fun provideEncryptedPreferences(
        preferences: Preferences,
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferencesImpl(preferences, encryptionUtil)
    }

    @Provides
    @Singleton
    fun provideSigner(): Signer {
        return Signer
    }

    @Provides
    @Singleton
    fun provideIconGenerator(): IconGenerator {
        return IconGenerator()
    }

    @Provides
    @Singleton
    fun provideClipboardManager(context: Context): ClipboardManager {
        return ClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
    }

    @Provides
    @Singleton
    fun provideDeviceVibrator(context: Context): DeviceVibrator {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return DeviceVibrator(vibrator)
    }

    @Provides
    @Singleton
    fun provideLanguagesHolder(): LanguagesHolder {
        return LanguagesHolder()
    }

    @Provides
    @Singleton
    fun provideAddressModelCreator(
        resourceManager: ResourceManager,
        iconGenerator: IconGenerator
    ): AddressIconGenerator = StatelessAddressIconGenerator(iconGenerator, resourceManager)

    @Provides
    @Caching
    fun provideCachingAddressModelCreator(
        delegate: AddressIconGenerator
    ): AddressIconGenerator = CachingAddressIconGenerator(delegate)

    @Provides
    @Singleton
    fun provideQrCodeGenerator(resourceManager: ResourceManager): QrCodeGenerator {
        return QrCodeGenerator(Color.BLACK, Color.WHITE, resourceManager)
    }

    @Provides
    @Singleton
    fun provideFileProvider(contextManager: ContextManager): FileProvider {
        return FileProviderImpl(contextManager.getContext())
    }

    @Provides
    @Singleton
    fun provideRandom(): Random = SecureRandom()

    @Provides
    @Singleton
    fun provideContentResolver(
        context: Context
    ): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideDefaultPagedKeysRetriever(): BulkRetriever {
        return BulkRetriever()
    }

    @Provides
    @Singleton
    fun provideValidationExecutor(
        resourceManager: ResourceManager
    ): ValidationExecutor {
        return ValidationExecutor(resourceManager)
    }

    @Provides
    @Singleton
    fun provideSecretStoreV1(
        encryptedPreferences: EncryptedPreferences
    ): SecretStoreV1 = SecretStoreV1Impl(encryptedPreferences)

    @Provides
    @Singleton
    fun provideSecretStoreV2(
        encryptedPreferences: EncryptedPreferences
    ) = SecretStoreV2(encryptedPreferences)

    @Provides
    @Singleton
    fun provideUpdatesMixin(): UpdatesMixin = UpdatesProvider()

    @Provides
    @Singleton
    fun provideNetworkStateMixin(): NetworkStateMixin = NetworkStateProvider()
}
