package jp.co.soramitsu.common.di

import android.content.ContentResolver
import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import java.util.Random

interface CommonApi {

    fun context(): Context

    fun provideResourceManager(): ResourceManager

    fun provideNetworkApiCreator(): NetworkApiCreator

    fun provideAppLinksProvider(): AppLinksProvider

    fun providePreferences(): Preferences

    fun provideEncryptedPreferences(): EncryptedPreferences

    fun provideBip39(): Bip39

    fun provideKeypairFactory(): KeypairFactory

    fun provideSS58Encoder(): SS58Encoder

    fun provideJunctionDecoder(): JunctionDecoder

    fun provideIconGenerator(): IconGenerator

    fun provideClipboardManager(): ClipboardManager

    fun provideDeviceVibrator(): DeviceVibrator

    fun signer(): Signer

    fun logger(): Logger

    fun contextManager(): ContextManager

    fun languagesHolder(): LanguagesHolder

    fun provideJsonMapper(): Gson

    fun socketService(): SocketService

    fun connectionManager(): ConnectionManager

    fun provideSocketSingleRequestExecutor(): SocketSingleRequestExecutor

    fun addressIconGenerator(): AddressIconGenerator

    fun networkStateMixin(): NetworkStateMixin

    fun qrCodeGenerator(): QrCodeGenerator

    fun fileProvider(): FileProvider

    fun random(): Random

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun contentResolver(): ContentResolver
}