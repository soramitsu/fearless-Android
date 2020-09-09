package jp.co.soramitsu.common.di.modules

import android.content.Context
import android.content.SharedPreferences
import android.os.Vibrator
import dagger.Module
import dagger.Provides
import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.resources.ResourceManagerImpl
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder

const val SHARED_PREFERENCES_FILE = "fearless_prefs"

@Module
class CommonModule {

    @Provides
    @ApplicationScope
    fun provideResourceManager(context: Context): ResourceManager {
        return ResourceManagerImpl(context)
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
}