package jp.co.soramitsu.account.api.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.extensions.fromHex

class PendulumPreInstalledAccountsScenario(
    private val accountRepository: AccountRepository,
    private val preferences: Preferences
) {

    companion object{
        private const val PENDULUM_CASE_KEY_PREFIX = "pendulum_mode"
        const val PENDULUM_FEATURE_TOGGLE_KEY = "pendulumCaseEnabled"
    }

    suspend fun import(qrContent: String): Result<Any> {
        val mnemonic = kotlin.runCatching {
            val bytes = qrContent.fromHex()
            String(bytes)
        }.getOrElse { return Result.failure(RuntimeException("Can't decode qr code.")) }
        return kotlin.runCatching {
            accountRepository.importFromMnemonic(
                mnemonic = mnemonic,
                accountName = "Pendulum ${mnemonic.split(' ').first()}",
                substrateDerivationPath = "",
                ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                selectedEncryptionType = CryptoType.SR25519,
                withEth = true,
                isBackedUp = true,
                googleBackupAddress = null
            )
        }.onSuccess {
            markAccountImportedForPendulum(it)
        }
    }

    private fun markAccountImportedForPendulum(walletId: Long) {
        preferences.putBoolean("$PENDULUM_CASE_KEY_PREFIX-$walletId", true)
    }

    fun isPendulumMode(walletId: Long): Boolean {
        return isFeatureEnabled() && preferences.contains("$PENDULUM_CASE_KEY_PREFIX-$walletId")
    }

    fun isFeatureEnabled(): Boolean {
        return BuildConfig.DEBUG || (preferences.contains(PENDULUM_FEATURE_TOGGLE_KEY) && preferences.getBoolean(
            PENDULUM_FEATURE_TOGGLE_KEY,
            false
        ))
    }
}