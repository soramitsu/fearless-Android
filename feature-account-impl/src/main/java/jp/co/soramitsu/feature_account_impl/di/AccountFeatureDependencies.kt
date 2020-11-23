package jp.co.soramitsu.feature_account_impl.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import java.util.Random

interface AccountFeatureDependencies {

    fun appLinksProvider(): AppLinksProvider

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun iconGenerator(): IconGenerator

    fun clipboardManager(): ClipboardManager

    fun context(): Context

    fun deviceVibrator(): DeviceVibrator

    fun userDao(): AccountDao

    fun nodeDao(): NodeDao

    fun languagesHolder(): LanguagesHolder

    fun socketSingleRequestExecutor(): SocketSingleRequestExecutor

    fun jsonMapper(): Gson

    fun addressIconGenerator(): AddressIconGenerator

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun random(): Random
}