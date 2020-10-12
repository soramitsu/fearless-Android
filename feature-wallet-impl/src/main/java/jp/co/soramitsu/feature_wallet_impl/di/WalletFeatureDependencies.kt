package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.RxWebSocketCreator
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface WalletFeatureDependencies {

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun iconGenerator(): IconGenerator

    fun clipboardManager(): ClipboardManager

    fun context(): Context

    fun accountRepository(): AccountRepository

    fun assetsDao(): AssetDao

    fun transactionsDao(): TransactionDao

    fun networkCreator(): NetworkApiCreator

    fun keypairFactory(): KeypairFactory

    fun signer(): Signer

    fun sS58Encoder(): SS58Encoder

    fun logger(): Logger

    fun socketSingleRequestExecutor(): SocketSingleRequestExecutor

    fun rxWebSocketCreator(): RxWebSocketCreator

    fun jsonMapper(): Gson

    fun addressIconGenerator(): AddressIconGenerator
}