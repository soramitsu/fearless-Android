package jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration

import android.annotation.SuppressLint
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.core.model.SecuritySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

private const val PREFS_PRIVATE_KEY = "private_%s"
private const val PREFS_SEED_MASK = "seed_%s"
private const val PREFS_DERIVATION_MASK = "derivation_%s"
private const val PREFS_ENTROPY_MASK = "entropy_%s"
private const val PREFS_MIGRATED_FROM_0_4_1_TO_1_0_0 = "migrated_from_0_4_1_to_1_0_0"

private object ScaleSigningData : Schema<ScaleSigningData>() {
    val PrivateKey by byteArray()
    val PublicKey by byteArray()

    val Nonce by byteArray().optional()
}

typealias SaveSourceCallback = suspend (String, SecuritySource) -> Unit

@SuppressLint("CheckResult")
class AccountDataMigration(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val bip39: Bip39,
    private val accountsDao: AccountDao
) {

    suspend fun migrationNeeded(): Boolean = withContext(Dispatchers.Default) {
        val migrated = preferences.getBoolean(PREFS_MIGRATED_FROM_0_4_1_TO_1_0_0, false)

        !migrated
    }

    suspend fun migrate(saveSourceCallback: SaveSourceCallback) = withContext(Dispatchers.Default) {
        val accounts = accountsDao.getAccounts()

        migrateAllAccounts(accounts, saveSourceCallback)

        preferences.putBoolean(PREFS_MIGRATED_FROM_0_4_1_TO_1_0_0, true)
    }

    private suspend fun migrateAllAccounts(accounts: List<AccountLocal>, saveSourceCallback: SaveSourceCallback) {
        accounts.forEach { migrateAccount(it.address, saveSourceCallback) }
    }

    private suspend fun migrateAccount(accountAddress: String, saveSourceCallback: SaveSourceCallback) {
        val oldKey = PREFS_PRIVATE_KEY.format(accountAddress)
        val oldRaw = encryptedPreferences.getDecryptedString(oldKey) ?: return
        val data = ScaleSigningData.read(oldRaw)

        val signingData = SigningData(
            publicKey = data[ScaleSigningData.PublicKey],
            privateKey = data[ScaleSigningData.PrivateKey],
            nonce = data[ScaleSigningData.Nonce]
        )

        val seedKey = PREFS_SEED_MASK.format(accountAddress)
        val seedValue = encryptedPreferences.getDecryptedString(seedKey)
        val seed = seedValue?.let { Hex.decode(it) }

        val derivationKey = PREFS_DERIVATION_MASK.format(accountAddress)
        val derivationPath = encryptedPreferences.getDecryptedString(derivationKey)

        val entropyKey = PREFS_ENTROPY_MASK.format(accountAddress)
        val entropyValue = encryptedPreferences.getDecryptedString(entropyKey)
        val entropy = entropyValue?.let { Hex.decode(it) }

        val securitySource = if (entropy != null) {
            val mnemonic = bip39.generateMnemonic(entropy)
            SecuritySource.Specified.Mnemonic(seed, signingData, mnemonic, derivationPath)
        } else {
            if (seed != null) {
                SecuritySource.Specified.Seed(seed, signingData, derivationPath)
            } else {
                SecuritySource.Unspecified(signingData)
            }
        }

        saveSourceCallback(accountAddress, securitySource)
    }
}