package jp.co.soramitsu.common.di.modules

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Vibrator
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActionsProvider
import jp.co.soramitsu.common.data.FileProviderImpl
import jp.co.soramitsu.common.data.network.AppLinksProvider
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
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import java.security.SecureRandom
import java.util.Random

const val SHARED_PREFERENCES_FILE = "fearless_prefs"

@Module
class CommonModule {

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
    fun provideBip39(): Bip39 {
        return Bip39()
    }

    @Provides
    @ApplicationScope
    fun provideKeypairFactory(): KeypairFactory {
        return KeypairFactory()
    }

    @Provides
    @ApplicationScope
    fun provideSS58Encoder(): SS58Encoder {
        return SS58Encoder()
    }

    @Provides
    @ApplicationScope
    fun provideJunctionDecoder(): JunctionDecoder {
        return JunctionDecoder()
    }

    @Provides
    @ApplicationScope
    fun provideSigner(): Signer {
        return Signer()
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
        sS58Encoder: SS58Encoder,
        iconGenerator: IconGenerator
    ): AddressIconGenerator = AddressIconGenerator(iconGenerator, sS58Encoder, resourceManager)

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
    fun provideExternalAccountActions(
        clipboardManager: ClipboardManager,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager
    ): ExternalAccountActions.Presentation {
        return ExternalAccountActionsProvider(clipboardManager, appLinksProvider, resourceManager)
    }

    @Provides
    @ApplicationScope
    fun provideContentResolver(
        context: Context
    ): ContentResolver {
        return context.contentResolver
    }
}