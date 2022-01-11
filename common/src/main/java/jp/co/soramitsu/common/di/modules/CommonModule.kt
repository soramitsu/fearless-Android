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
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.interfaces.FileProvider
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

const val SHARED_PREFERENCES_FILE = "fearless_prefs"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Caching

@Module
class CommonModule {

    @Provides
    @ApplicationScope
    fun provideComputationalCache() = ComputationalCache()

    @Provides
    @ApplicationScope
    fun imageLoader(context: Context) = ImageLoader.Builder(context)
        .componentRegistry {
            add(SvgDecoder(context))
        }
        .build()

    @Provides
    @ApplicationScope
    fun provideResourceManager(contextManager: ContextManager): ResourceManager {
        return ResourceManagerImpl(contextManager)
    }

    @Provides
    @ApplicationScope
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    @Provides
    @ApplicationScope
    fun providePreferences(sharedPreferences: SharedPreferences): Preferences {
        return PreferencesImpl(sharedPreferences)
    }

    @Provides
    @ApplicationScope
    fun provideEncryptionUtil(context: Context): EncryptionUtil {
        return EncryptionUtil(context)
    }

    @Provides
    @ApplicationScope
    fun provideEncryptedPreferences(
        preferences: Preferences,
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferencesImpl(preferences, encryptionUtil)
    }

    @Provides
    @ApplicationScope
    fun provideSigner(): Signer {
        return Signer
    }

    @Provides
    @ApplicationScope
    fun provideIconGenerator(): IconGenerator {
        return IconGenerator()
    }

    @Provides
    @ApplicationScope
    fun provideClipboardManager(context: Context): ClipboardManager {
        return ClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
    }

    @Provides
    @ApplicationScope
    fun provideDeviceVibrator(context: Context): DeviceVibrator {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return DeviceVibrator(vibrator)
    }

    @Provides
    @ApplicationScope
    fun provideLanguagesHolder(): LanguagesHolder {
        return LanguagesHolder()
    }

    @Provides
    @ApplicationScope
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
    @ApplicationScope
    fun provideQrCodeGenerator(): QrCodeGenerator {
        return QrCodeGenerator(Color.BLACK, Color.WHITE)
    }

    @Provides
    @ApplicationScope
    fun provideFileProvider(contextManager: ContextManager): FileProvider {
        return FileProviderImpl(contextManager.getContext())
    }

    @Provides
    @ApplicationScope
    fun provideRandom(): Random = SecureRandom()

    @Provides
    @ApplicationScope
    fun provideContentResolver(
        context: Context
    ): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @ApplicationScope
    fun provideDefaultPagedKeysRetriever(): BulkRetriever {
        return BulkRetriever()
    }

    @Provides
    @ApplicationScope
    fun provideValidationExecutor(
        resourceManager: ResourceManager
    ): ValidationExecutor {
        return ValidationExecutor(resourceManager)
    }

    @Provides
    @ApplicationScope
    fun provideSecretStoreV1(
        encryptedPreferences: EncryptedPreferences
    ): SecretStoreV1 = SecretStoreV1Impl(encryptedPreferences)

    @Provides
    @ApplicationScope
    fun provideSecretStoreV2(
        encryptedPreferences: EncryptedPreferences
    ) = SecretStoreV2(encryptedPreferences)
}
