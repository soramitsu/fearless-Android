package jp.co.soramitsu.common.di

import android.content.ContentResolver
import android.content.Context
import coil.ImageLoader
import com.google.gson.Gson
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.di.modules.Caching
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import java.util.Random

interface CommonApi {

    fun computationalCache(): ComputationalCache

    fun imageLoader(): ImageLoader

    fun context(): Context

    fun provideResourceManager(): ResourceManager

    fun provideNetworkApiCreator(): NetworkApiCreator

    fun provideAppLinksProvider(): AppLinksProvider

    fun providePreferences(): Preferences

    fun provideEncryptedPreferences(): EncryptedPreferences

    fun provideIconGenerator(): IconGenerator

    fun provideClipboardManager(): ClipboardManager

    fun provideDeviceVibrator(): DeviceVibrator

    fun signer(): Signer

    fun logger(): Logger

    fun contextManager(): ContextManager

    fun languagesHolder(): LanguagesHolder

    fun provideJsonMapper(): Gson

    fun socketServiceCreator(): SocketService

    fun provideSocketSingleRequestExecutor(): SocketSingleRequestExecutor

    fun addressIconGenerator(): AddressIconGenerator

    @Caching
    fun cachingAddressIconGenerator(): AddressIconGenerator

    fun networkStateMixin(): NetworkStateMixin

    fun qrCodeGenerator(): QrCodeGenerator

    fun fileProvider(): FileProvider

    fun random(): Random

    fun contentResolver(): ContentResolver

    fun httpExceptionHandler(): HttpExceptionHandler

    fun defaultPagedKeysRetriever(): BulkRetriever

    fun validationExecutor(): ValidationExecutor

    fun secretStoreV1(): SecretStoreV1

    fun secretStoreV2(): SecretStoreV2

    fun updatesMixin(): UpdatesMixin
}
