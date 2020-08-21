package jp.co.soramitsu.feature_account_impl.di

import android.content.Context
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao

interface AccountFeatureDependencies {

    fun appLinksProvider(): AppLinksProvider

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun context(): Context

    fun deviceVibrator(): DeviceVibrator

    fun userDao(): UserDao

    fun nodeDao(): NodeDao
}