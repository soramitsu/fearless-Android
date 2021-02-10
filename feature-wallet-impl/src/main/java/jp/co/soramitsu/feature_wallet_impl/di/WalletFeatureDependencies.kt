package jp.co.soramitsu.feature_wallet_impl.di

import android.content.ContentResolver
import com.google.gson.Gson
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface WalletFeatureDependencies {

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun iconGenerator(): IconGenerator

    fun clipboardManager(): ClipboardManager

    fun contentResolver(): ContentResolver

    fun accountRepository(): AccountRepository

    fun assetsDao(): AssetDao

    fun transactionsDao(): TransactionDao

    fun networkCreator(): NetworkApiCreator

    fun keypairFactory(): KeypairFactory

    fun signer(): Signer

    fun sS58Encoder(): SS58Encoder

    fun logger(): Logger

    fun socketService(): SocketService

    fun jsonMapper(): Gson

    fun addressIconGenerator(): AddressIconGenerator

    fun appLinksProvider(): AppLinksProvider

    fun qrCodeGenerator(): QrCodeGenerator

    fun fileProvider(): FileProvider

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun httpExceptionHandler(): HttpExceptionHandler

    fun phishingAddressesDao(): PhishingAddressDao
}